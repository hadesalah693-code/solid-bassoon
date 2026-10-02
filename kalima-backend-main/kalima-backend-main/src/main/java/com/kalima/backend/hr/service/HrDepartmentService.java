package com.kalima.backend.hr.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.dto.HrDepartmentRequest;
import com.kalima.backend.hr.dto.HrDepartmentResponse;
import com.kalima.backend.hr.entity.HrDepartment;
import com.kalima.backend.hr.repository.HrDepartmentRepository;
import com.kalima.backend.hr.repository.HrDepartmentSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class HrDepartmentService {

    private final HrDepartmentRepository repository;
    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public HrDepartmentService(HrDepartmentRepository repository,
                               CompanyRepository companyRepository,
                               PersonRepository personRepository,
                               CurrentUserService currentUserService,
                               AuditLogService auditLogService) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<HrDepartmentResponse> search(Boolean active, String search, Pageable pageable) {
        String normalized = trimToNull(search);
        return repository.findAll(HrDepartmentSpecifications.filtered(active, normalized), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<HrDepartmentResponse> listActive() {
        return repository.listActive().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HrDepartmentResponse findById(UUID id) {
        return toResponse(requireDepartment(id));
    }

    public HrDepartmentResponse create(HrDepartmentRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        String name = requireText(request.name(), "name");
        UUID tenantId = TenantContext.getRequired();
        if (repository.existsByCompany_IdAndName(tenantId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "department name already exists");
        }

        HrDepartment entity = new HrDepartment();
        entity.setCompany(currentTenantReference());
        entity.setName(name);
        entity.setCode(trimToNull(request.code()));
        entity.setParent(resolveParent(request.parentId(), null));
        entity.setManager(resolveManager(request.managerId()));
        entity.setDescription(trimToNull(request.description()));
        entity.setActive(request.active() == null || request.active());

        HrDepartment saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.HR_DEPARTMENT_CREATED,
                actor,
                "HrDepartment",
                saved.getId(),
                Map.of("name", saved.getName()));
        return toResponse(saved);
    }

    public HrDepartmentResponse update(UUID id, HrDepartmentRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        HrDepartment entity = requireDepartment(id);

        if (request.name() != null) {
            String name = requireText(request.name(), "name");
            if (repository.existsByCompany_IdAndNameAndIdNot(TenantContext.getRequired(), name, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "department name already exists");
            }
            entity.setName(name);
        }
        if (request.code() != null) {
            entity.setCode(trimToNull(request.code()));
        }
        if (request.parentId() != null) {
            entity.setParent(resolveParent(request.parentId(), id));
        }
        if (request.managerId() != null) {
            entity.setManager(resolveManager(request.managerId()));
        }
        if (request.description() != null) {
            entity.setDescription(trimToNull(request.description()));
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        HrDepartment saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.HR_DEPARTMENT_UPDATED,
                actor,
                "HrDepartment",
                saved.getId(),
                Map.of("name", saved.getName()));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        Person actor = currentUserService.requireCurrentPerson();
        HrDepartment entity = requireDepartment(id);
        repository.delete(entity);
        auditLogService.recordSuccess(
                AuditAction.HR_DEPARTMENT_DELETED,
                actor,
                "HrDepartment",
                id,
                Map.of("name", entity.getName()));
    }

    HrDepartment requireDepartment(UUID id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "department not found"));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private HrDepartment resolveParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(selfId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a department cannot be its own parent");
        }
        HrDepartment parent = requireDepartment(parentId);
        // Walk up the tree so a cycle (A → B → A) cannot be created.
        HrDepartment cursor = parent;
        int depth = 0;
        while (cursor != null && depth < 32) {
            if (cursor.getId().equals(selfId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "department hierarchy would form a cycle");
            }
            HrDepartment next = cursor.getParent();
            cursor = next == null ? null : repository.findScopedById(next.getId()).orElse(null);
            depth++;
        }
        return parent;
    }

    private Person resolveManager(UUID managerId) {
        if (managerId == null) {
            return null;
        }
        return personRepository.findScopedById(managerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "manager not found"));
    }

    private HrDepartmentResponse toResponse(HrDepartment d) {
        return new HrDepartmentResponse(
                d.getId(),
                d.getName(),
                d.getCode(),
                d.getParent() != null ? d.getParent().getId() : null,
                d.getParent() != null ? d.getParent().getName() : null,
                toPartySummary(d.getManager()),
                d.getDescription(),
                d.isActive());
    }

    private static PartySummary toPartySummary(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(person.getId(), person.getFullName(), person.getSystemRole());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireText(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return trimmed;
    }
}
