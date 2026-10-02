package com.kalima.backend.template.service;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.template.dto.CreateTemplateRequest;
import com.kalima.backend.template.dto.TemplateResponse;
import com.kalima.backend.template.dto.UpdateTemplateRequest;
import com.kalima.backend.template.entity.Template;
import com.kalima.backend.template.repository.TemplateRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * CRUD service for fillable form / document templates.
 *
 * <p>Tenant-isolation: every read/write goes through the Hibernate
 * {@code tenantFilter} on {@link Template}, and {@link #currentTenantReference()}
 * is the only path used to attach a {@link Company} on insert. The service
 * therefore never relies on caller-supplied company ids.
 *
 * <p>Document <em>generation</em> intentionally lives in
 * {@link DocumentGenerationService} so this class stays focused on the
 * template lifecycle.
 */
@Service
@Transactional
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final CompanyRepository companyRepository;

    public TemplateService(TemplateRepository templateRepository,
                           CompanyRepository companyRepository) {
        this.templateRepository = templateRepository;
        this.companyRepository = companyRepository;
    }

    public TemplateResponse create(CreateTemplateRequest request) {
        Template entity = new Template();
        entity.setCompany(currentTenantReference());
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setContent(request.content());
        entity.setDefault(Boolean.TRUE.equals(request.isDefault()));
        return toResponse(templateRepository.save(entity));
    }

    public TemplateResponse update(UUID id, UpdateTemplateRequest request) {
        Template entity = loadOrThrow(id);
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setContent(request.content());
        entity.setDefault(Boolean.TRUE.equals(request.isDefault()));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public TemplateResponse findById(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(DomainEnums.TemplateType type) {
        return templateRepository.findAllByOptionalType(
                        type,
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void delete(UUID id) {
        Template entity = loadOrThrow(id);
        templateRepository.delete(entity);
    }

    /**
     * Tenant-scoped lookup intended for sibling services
     * ({@link DocumentGenerationService}). Returns the entity (not a DTO) so
     * the caller can read {@code content} directly when rendering.
     */
    @Transactional(readOnly = true)
    public Template loadEntity(UUID id) {
        return loadOrThrow(id);
    }

    private Template loadOrThrow(UUID id) {
        return templateRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Template not found: " + id));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private TemplateResponse toResponse(Template t) {
        return new TemplateResponse(
                t.getId(),
                t.getName(),
                t.getType(),
                t.getContent(),
                t.isDefault(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
