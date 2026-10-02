package com.kalima.backend.staff.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.auth.support.EmailNormalization;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.staff.dto.ChangePasswordRequest;
import com.kalima.backend.staff.dto.ResetPasswordRequest;
import com.kalima.backend.staff.dto.StaffCreateRequest;
import com.kalima.backend.staff.dto.StaffResponse;
import com.kalima.backend.staff.dto.StaffUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Staff (non-CLIENT persons) management. Handles creation with password
 * hashing, profile updates, soft-disable / enable, admin password reset,
 * and self password change.
 *
 * <p>Strict rules:
 * <ul>
 *   <li>Only non-CLIENT system roles are allowed here.</li>
 *   <li>Email is globally unique and immutable once set (stored lowercase).</li>
 *   <li>System role is immutable once set (promotion/demotion requires manual
 *       intervention by design).</li>
 *   <li>All lookups are tenant-scoped via {@code findScopedById}.</li>
 * </ul>
 */
@Service
@Transactional
public class StaffService {

    private static final Set<DomainEnums.SystemRole> STAFF_ROLES = Set.of(
            DomainEnums.SystemRole.ADMIN,
            DomainEnums.SystemRole.LAWYER,
            DomainEnums.SystemRole.SENIOR_LAWYER,
            DomainEnums.SystemRole.SECRETARY,
            DomainEnums.SystemRole.ACCOUNTANT
    );

    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public StaffService(PersonRepository personRepository,
                        CompanyRepository companyRepository,
                        PasswordEncoder passwordEncoder,
                        CurrentUserService currentUserService,
                        AuditLogService auditLogService) {
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    public StaffResponse create(StaffCreateRequest request) {
        ensureStaffRole(request.systemRole());

        String email = EmailNormalization.normalize(request.email());
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (personRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "email already exists: " + email);
        }

        Person person = new Person();
        person.setCompany(currentTenantReference());
        person.setPersonType(DomainEnums.PersonType.INDIVIDUAL);
        person.setSystemRole(request.systemRole());
        person.setFullName(normalize(request.fullName()));
        person.setEmail(email);
        person.setPasswordHash(passwordEncoder.encode(request.password()));
        person.setNationalId(normalize(request.nationalId()));
        person.setPhone(normalize(request.phone()));
        person.setAddress(normalize(request.address()));
        person.setNotes(normalize(request.notes()));
        person.setActive(true);

        Person saved = personRepository.save(person);

        auditLogService.recordSuccess(
                AuditAction.STAFF_CREATED,
                currentUserService.requireCurrentPerson(),
                "Person",
                saved.getId(),
                Map.of(
                        "role", saved.getSystemRole().name(),
                        "email", saved.getEmail(),
                        "fullName", saved.getFullName() == null ? "" : saved.getFullName()
                ));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<StaffResponse> findAll(DomainEnums.SystemRole roleFilter, Pageable pageable) {
        Page<Person> page;
        if (roleFilter != null) {
            if (roleFilter == DomainEnums.SystemRole.CLIENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "CLIENT is not a staff role; use /api/persons?role=CLIENT instead");
            }
            page = personRepository.findBySystemRoleAndArchivedAtIsNull(roleFilter, pageable);
        } else {
            page = personRepository.findBySystemRoleNotAndArchivedAtIsNull(
                    DomainEnums.SystemRole.CLIENT, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public StaffResponse findById(UUID id) {
        return toResponse(loadStaff(id));
    }

    public StaffResponse update(UUID id, StaffUpdateRequest request) {
        Person person = loadStaff(id);

        Map<String, Object> changes = new HashMap<>();
        trackChange(changes, "fullName", person.getFullName(), normalize(request.fullName()));
        trackChange(changes, "nationalId", person.getNationalId(), normalize(request.nationalId()));
        trackChange(changes, "phone", person.getPhone(), normalize(request.phone()));
        trackChange(changes, "address", person.getAddress(), normalize(request.address()));
        trackChange(changes, "notes", person.getNotes(), normalize(request.notes()));

        person.setFullName(normalize(request.fullName()));
        person.setNationalId(normalize(request.nationalId()));
        person.setPhone(normalize(request.phone()));
        person.setAddress(normalize(request.address()));
        person.setNotes(normalize(request.notes()));

        auditLogService.recordSuccess(
                AuditAction.STAFF_UPDATED,
                currentUserService.requireCurrentPerson(),
                "Person",
                person.getId(),
                Map.of("changes", changes));
        return toResponse(person);
    }

    /** Soft-disable. Prevents self-lockout. */
    public void disable(UUID id) {
        Person person = loadStaff(id);
        Person current = currentUserService.requireCurrentPerson();
        if (current.getId().equals(person.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot disable your own account");
        }
        if (!person.isActive()) {
            return;
        }
        person.setActive(false);
        auditLogService.recordSuccess(
                AuditAction.STAFF_DISABLED, current,
                "Person", person.getId(),
                Map.of("targetEmail", person.getEmail()));
    }

    public void enable(UUID id) {
        Person person = loadStaff(id);
        if (person.isActive()) {
            return;
        }
        person.setActive(true);
        auditLogService.recordSuccess(
                AuditAction.STAFF_ENABLED,
                currentUserService.requireCurrentPerson(),
                "Person", person.getId(),
                Map.of("targetEmail", person.getEmail()));
    }

    public void resetPassword(UUID id, ResetPasswordRequest request) {
        Person person = loadStaff(id);
        person.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        auditLogService.recordSuccess(
                AuditAction.PASSWORD_RESET,
                currentUserService.requireCurrentPerson(),
                "Person", person.getId(),
                Map.of("targetEmail", person.getEmail()));
    }

    /**
     * Self-service password change. Works for any authenticated person that has
     * a password (typically staff; clients don't have one by default).
     */
    public void changeOwnPassword(ChangePasswordRequest request) {
        Person current = currentUserService.requireCurrentPerson();
        String currentHash = current.getPasswordHash();
        if (currentHash == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Current account has no password set");
        }
        if (!passwordEncoder.matches(request.oldPassword(), currentHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Old password is incorrect");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New password must be different from the old one");
        }
        current.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        auditLogService.recordSuccess(
                AuditAction.PASSWORD_CHANGED, current,
                "Person", current.getId(),
                Map.of("self", true));
    }

    private void trackChange(Map<String, Object> sink, String key,
                             String oldValue, String newValue) {
        if (!java.util.Objects.equals(oldValue, newValue)) {
            sink.put(key, Map.of(
                    "old", oldValue == null ? "" : oldValue,
                    "new", newValue == null ? "" : newValue));
        }
    }

    private Person loadStaff(UUID id) {
        Person person = personRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Staff not found: " + id));
        if (person.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            // Pretend it doesn't exist — don't leak that a CLIENT with this id exists.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found: " + id);
        }
        if (person.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found: " + id);
        }
        return person;
    }

    private Company currentTenantReference() {
        UUID tenantId = TenantContext.getRequired();
        return companyRepository.getReferenceById(tenantId);
    }

    private void ensureStaffRole(DomainEnums.SystemRole role) {
        if (role == null || !STAFF_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "systemRole must be one of: " + STAFF_ROLES);
        }
    }

    private StaffResponse toResponse(Person p) {
        return new StaffResponse(
                p.getId(),
                p.getCompany() != null ? p.getCompany().getId() : null,
                p.getSystemRole(),
                p.getFullName(),
                p.getEmail(),
                p.getNationalId(),
                p.getPhone(),
                p.getAddress(),
                p.getNotes(),
                p.isActive(),
                p.getLastLoginAt(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    /**
     * Trim-only normalization for non-email text fields. Email uses
     * {@link EmailNormalization#normalize(String)}.
     */
    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
