package com.kalima.backend.template.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.template.dto.GenerateDocumentRequest;
import com.kalima.backend.template.dto.GeneratedDocumentResponse;
import com.kalima.backend.template.dto.UpdateDocumentStatusRequest;
import com.kalima.backend.template.service.DocumentGenerationService;
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
 * REST entry-points for documents rendered from a {@code Template}.
 *
 * <p>All access is staff-scoped; CLIENT role is intentionally excluded
 * because generated documents may include unsent drafts (quotations,
 * invoices, contracts) that should only become visible to a client through
 * an explicit "send" workflow added later.
 *
 * <p>Tenant isolation, optional client/case linkage, and placeholder
 * rendering all live in {@link DocumentGenerationService}; this controller
 * does not duplicate any of that logic.
 */
@RestController
@RequestMapping("/api/documents")
public class GeneratedDocumentController {

    private final DocumentGenerationService documentService;

    public GeneratedDocumentController(DocumentGenerationService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<GeneratedDocumentResponse> generate(
            @Valid @RequestBody GenerateDocumentRequest request,
            UriComponentsBuilder uriBuilder) {
        GeneratedDocumentResponse created = documentService.generate(request);
        URI location = uriBuilder.path("/api/documents/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<GeneratedDocumentResponse> list(
            @RequestParam(name = "status", required = false) DomainEnums.DocumentStatus status,
            @RequestParam(name = "caseId", required = false) UUID caseId,
            @RequestParam(name = "clientId", required = false) UUID clientId) {
        return documentService.list(status, caseId, clientId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public GeneratedDocumentResponse getById(@PathVariable UUID id) {
        return documentService.findById(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public GeneratedDocumentResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentStatusRequest request) {
        return documentService.updateStatus(id, request);
    }
}
