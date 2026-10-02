package com.kalima.backend.hr.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.dto.HrPositionRequest;
import com.kalima.backend.hr.dto.HrPositionResponse;
import com.kalima.backend.hr.entity.HrDepartment;
import com.kalima.backend.hr.entity.HrPosition;
import com.kalima.backend.hr.repository.HrDepartmentRepository;
import com.kalima.backend.hr.repository.HrPositionRepository;
import com.kalima.backend.hr.repository.HrPositionSpecifications;
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
public class HrPositionService {

    private final HrPositionRepository repository;
    private final HrDepartmentRepository departmentRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public HrPositionService(HrPositionRepository repository,
                             HrDepartmentRepository departmentRepository,
                             CompanyRepository companyRepository,
                             CurrentUserService currentUserService,
                             AuditLogService auditLogService) {
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<HrPositionResponse> search(UUID departmentId, Boolean active, String search, Pageable pageable) {
        String normalized = trimToNull(search);
        return repository.findAll(
                        HrPositionSpecifications.filtered(departmentId, active, normalized),
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<HrPositionResponse> listActive() {
        return repository.listActive().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HrPositionResponse findById(UUID id) {
        return toResponse(requirePosition(id));
    }

    public HrPositionResponse create(HrPositionRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        String title = requireText(request.title(), "title");
        UUID tenantId = TenantContext.getRequired();
        if (repository.existsByCompany_IdAndTitle(tenantId, title)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "position title already exists");
        }

        HrPosition entity = new HrPosition();
        entity.setCompany(currentTenantReference());
        entity.setTitle(title);
        entity.setCode(trimToNull(request.code()));
        entity.setDepartment(resolveDepartment(request.departmentId()));
        entity.setDescription(trimToNull(request.description()));
        entity.setActive(request.active() == null || request.active());

        HrPosition saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.HR_POSITION_CREATED,
                actor,
                "HrPosition",
                saved.getId(),
                Map.of("title", saved.getTitle()));
        return toResponse(saved);
    }

    public HrPositionResponse update(UUID id, HrPositionRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        HrPosition entity = requirePosition(id);

        if (request.title() != null) {
            String title = requireText(request.title(), "title");
            if (repository.existsByCompany_IdAndTitleAndIdNot(TenantContext.getRequired(), title, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "position title already exists");
            }
            entity.setTitle(title);
        }
        if (request.code() != null) {
            entity.setCode(trimToNull(request.code()));
        }
        if (request.departmentId() != null) {
            entity.setDepartment(resolveDepartment(request.departmentId()));
        }
        if (request.description() != null) {
            entity.setDescription(trimToNull(request.description()));
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        HrPosition saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.HR_POSITION_UPDATED,
                actor,
                "HrPosition",
                saved.getId(),
                Map.of("title", saved.getTitle()));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        Person actor = currentUserService.requireCurrentPerson();
        HrPosition entity = requirePosition(id);
        repository.delete(entity);
        auditLogService.recordSuccess(
                AuditAction.HR_POSITION_DELETED,
                actor,
                "HrPosition",
                id,
                Map.of("title", entity.getTitle()));
    }

    HrPosition requirePosition(UUID id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "position not found"));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private HrDepartment resolveDepartment(UUID departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findScopedById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "department not found"));
    }

    private HrPositionResponse toResponse(HrPosition p) {
        return new HrPositionResponse(
                p.getId(),
                p.getTitle(),
                p.getCode(),
                p.getDepartment() != null ? p.getDepartment().getId() : null,
                p.getDepartment() != null ? p.getDepartment().getName() : null,
                p.getDescription(),
                p.isActive());
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
