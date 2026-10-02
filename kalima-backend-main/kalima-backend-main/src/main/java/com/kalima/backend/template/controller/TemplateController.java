package com.kalima.backend.template.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.template.dto.CreateTemplateRequest;
import com.kalima.backend.template.dto.TemplateResponse;
import com.kalima.backend.template.dto.UpdateTemplateRequest;
import com.kalima.backend.template.service.TemplateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST entry-points for fillable form / document templates.
 *
 * <p>Templates are internal staff tooling (quotations, invoices, contracts,
 * power of attorney …), so endpoints are restricted to non-CLIENT roles
 * mirroring {@code CaseController}. Tenant isolation is enforced inside
 * {@link TemplateService} via the existing Hibernate {@code tenantFilter}.
 *
 * <p>{@code PATCH /{id}} accepts a full replacement of the editable fields,
 * matching the convention already used by {@code PUT /api/cases/{id}}.
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<TemplateResponse> list(
            @RequestParam(name = "type", required = false) DomainEnums.TemplateType type) {
        return templateService.list(type);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','SECRETARY')")
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody CreateTemplateRequest request,
            UriComponentsBuilder uriBuilder) {
        TemplateResponse created = templateService.create(request);
        URI location = uriBuilder.path("/api/templates/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public TemplateResponse getById(@PathVariable UUID id) {
        return templateService.findById(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','SECRETARY')")
    public TemplateResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateTemplateRequest request) {
        return templateService.update(id, request);
    }
}
