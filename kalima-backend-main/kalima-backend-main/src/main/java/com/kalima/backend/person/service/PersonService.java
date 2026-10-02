package com.kalima.backend.person.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.auth.support.EmailNormalization;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.caseparticipant.repository.CaseParticipantRepository;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.person.dto.PersonRequest;
import com.kalima.backend.person.dto.PersonResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class PersonService {

    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CaseFileRepository caseFileRepository;
    private final CaseParticipantRepository caseParticipantRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    public PersonService(PersonRepository personRepository,
                         CompanyRepository companyRepository,
                         CaseFileRepository caseFileRepository,
                         CaseParticipantRepository caseParticipantRepository,
                         CurrentUserService currentUserService,
                         AuditLogService auditLogService,
                         PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.caseFileRepository = caseFileRepository;
        this.caseParticipantRepository = caseParticipantRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
    }

    public PersonResponse create(PersonRequest request) {
        DomainEnums.SystemRole role = resolveRole(request.systemRole());
        ensureSupportedRole(role);
        validateTypeSpecificFields(request);
        validateOptionalClientLogin(request, null);
        Person person = new Person();
        person.setCompany(currentTenantReference());
        person.setSystemRole(role);
        person.setActive(true);
        applyRequest(person, request);
        if (trimToNull(request.password()) != null) {
            person.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        Person saved = personRepository.save(person);

        auditLogService.recordSuccess(
                AuditAction.PERSON_CREATED,
                currentUserService.requireCurrentPerson(),
                "Person", saved.getId(),
                Map.of(
                        "role", saved.getSystemRole().name(),
                        "personType", saved.getPersonType().name(),
                        "displayName", displayNameOf(saved)
                ));
        return toResponse(saved);
    }

    /**
     * Returns a managed Company reference for the current tenant. Used to
     * stamp {@code company_id} on new tenant-scoped rows.
     */
    private Company currentTenantReference() {
        UUID tenantId = TenantContext.getRequired();
        return companyRepository.getReferenceById(tenantId);
    }

    @Transactional(readOnly = true)
    public Page<PersonResponse> findAll(DomainEnums.SystemRole roleFilter, String search,
                                        Pageable pageable) {
        String q = trimToNull(search);
        if (q != null) {
            return personRepository.searchActive(roleFilter, q, pageable).map(this::toResponse);
        }
        Page<Person> page = roleFilter == null
                ? personRepository.findByArchivedAtIsNull(pageable)
                : personRepository.findBySystemRoleAndArchivedAtIsNull(roleFilter, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PersonResponse findById(UUID id) {
        return toResponse(loadPerson(id));
    }

    public PersonResponse update(UUID id, PersonRequest request) {
        DomainEnums.SystemRole requestedRole = resolveRole(request.systemRole());
        ensureSupportedRole(requestedRole);
        validateTypeSpecificFields(request);
        Person person = loadPerson(id);
        validateOptionalClientLogin(request, person.getId());
        if (person.getSystemRole() != requestedRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Changing systemRole is not supported yet");
        }

        Map<String, Object> changes = new HashMap<>();
        trackChange(changes, "fullName", person.getFullName(), trimToNull(request.fullName()));
        trackChange(changes, "companyName", person.getCompanyName(), trimToNull(request.companyName()));
        trackChange(
                changes,
                "email",
                EmailNormalization.normalize(person.getEmail()),
                EmailNormalization.normalize(request.email()));
        trackChange(changes, "phone", person.getPhone(), trimToNull(request.phone()));

        applyRequest(person, request);

        auditLogService.recordSuccess(
                AuditAction.PERSON_UPDATED,
                currentUserService.requireCurrentPerson(),
                "Person", person.getId(),
                Map.of("changes", changes));
        return toResponse(person);
    }

    /**
     * Soft-archive: set {@code archivedAt}. Fails with 409 if the person is
     * still referenced by a case (client/lawyer/creator) or by any case
     * participant row — hard delete is not used for those cases.
     */
    public PersonResponse archive(UUID id) {
        Person person = personRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Person not found: " + id));
        if (person.getArchivedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Person is already archived: " + id);
        }
        if (isReferencedByCaseOrParticipation(person.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot archive this person: still referenced by cases or case participants. "
                            + "Remove or reassign those links first.");
        }
        person.setArchivedAt(LocalDateTime.now());
        Map<String, Object> meta = Map.of(
                "role", person.getSystemRole().name(),
                "email", person.getEmail() == null ? "" : person.getEmail(),
                "displayName", displayNameOf(person));
        auditLogService.recordSuccess(
                AuditAction.PERSON_ARCHIVED,
                currentUserService.requireCurrentPerson(),
                "Person", person.getId(), meta);
        return toResponse(person);
    }

    private boolean isReferencedByCaseOrParticipation(UUID personId) {
        if (caseFileRepository.existsCaseReferencingPerson(personId)) {
            return true;
        }
        return caseParticipantRepository.existsByPersonId(personId);
    }

    private String displayNameOf(Person p) {
        if (p.getFullName() != null && !p.getFullName().isBlank()) {
            return p.getFullName();
        }
        return p.getCompanyName() == null ? "" : p.getCompanyName();
    }

    private void trackChange(Map<String, Object> sink, String key,
                             String oldValue, String newValue) {
        if (!java.util.Objects.equals(oldValue, newValue)) {
            sink.put(key, Map.of(
                    "old", oldValue == null ? "" : oldValue,
                    "new", newValue == null ? "" : newValue));
        }
    }

    private Person loadPerson(UUID id) {
        Person person = personRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found: " + id));
        if (person.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found: " + id);
        }
        return person;
    }

    private DomainEnums.SystemRole resolveRole(DomainEnums.SystemRole requested) {
        return requested == null ? DomainEnums.SystemRole.CLIENT : requested;
    }

    private void ensureSupportedRole(DomainEnums.SystemRole role) {
        if (role != DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only CLIENT persons can be created through this endpoint in the current phase");
        }
    }

    private void validateTypeSpecificFields(PersonRequest request) {
        if (request.personType() == DomainEnums.PersonType.INDIVIDUAL
                && isBlank(request.fullName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fullName is required for INDIVIDUAL persons");
        }
        if (request.personType() == DomainEnums.PersonType.COMPANY
                && isBlank(request.companyName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "companyName is required for COMPANY persons");
        }
    }

    private void validateOptionalClientLogin(PersonRequest request, UUID existingPersonId) {
        String password = trimToNull(request.password());
        String email = EmailNormalization.normalize(request.email());
        if (password != null && email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "email is required when creating a client login account");
        }
        if (email != null) {
            personRepository.findByEmail(email).ifPresent(existing -> {
                if (existingPersonId == null || !existing.getId().equals(existingPersonId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "email already exists: " + email);
                }
            });
        }
    }

    private void applyRequest(Person person, PersonRequest request) {
        person.setPersonType(request.personType());
        person.setFullName(trimToNull(request.fullName()));
        person.setNationalId(trimToNull(request.nationalId()));
        person.setCompanyName(trimToNull(request.companyName()));
        person.setCommercialRegister(trimToNull(request.commercialRegister()));
        person.setPhone(trimToNull(request.phone()));
        person.setEmail(EmailNormalization.normalize(request.email()));
        person.setAddress(trimToNull(request.address()));
        person.setNotes(trimToNull(request.notes()));
    }

    private PersonResponse toResponse(Person person) {
        return new PersonResponse(
                person.getId(),
                person.getPersonType(),
                person.getSystemRole(),
                person.getFullName(),
                person.getCompanyName(),
                person.getNationalId(),
                person.getCommercialRegister(),
                person.getPhone(),
                person.getEmail(),
                person.getAddress(),
                person.getNotes(),
                person.isActive(),
                person.getLastLoginAt(),
                person.getArchivedAt(),
                person.getCreatedAt(),
                person.getUpdatedAt()
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
