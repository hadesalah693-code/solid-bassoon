package com.kalima.backend.delegation.controller;

import com.kalima.backend.delegation.dto.DelegationDocumentResponse;
import com.kalima.backend.delegation.service.DelegationDocumentService;
import com.kalima.backend.domain.entity.DomainEnums;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/delegations/{delegationId}/documents")
public class DelegationDocumentController {

    private final DelegationDocumentService service;

    public DelegationDocumentController(DelegationDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DelegationDocumentResponse> list(@PathVariable UUID delegationId) {
        return service.list(delegationId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DelegationDocumentResponse> upload(
            @PathVariable UUID delegationId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "documentType", required = false) DomainEnums.DelegationDocumentType documentType,
            UriComponentsBuilder uriBuilder) {
        DelegationDocumentResponse created = service.upload(delegationId, file, description, documentType);
        URI location = uriBuilder
                .path("/api/delegations/{delegationId}/documents/{documentId}/download")
                .buildAndExpand(delegationId, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/from-ocr/{ocrRecordId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DelegationDocumentResponse> attachFromOcr(
            @PathVariable UUID delegationId,
            @PathVariable UUID ocrRecordId,
            UriComponentsBuilder uriBuilder) {
        DelegationDocumentResponse created = service.attachFromOcrRecord(delegationId, ocrRecordId);
        URI location = uriBuilder
                .path("/api/delegations/{delegationId}/documents/{documentId}/download")
                .buildAndExpand(delegationId, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public ResponseEntity<Resource> download(
            @PathVariable UUID delegationId,
            @PathVariable UUID documentId) {
        DelegationDocumentService.DownloadableDocument doc =
                service.download(delegationId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .contentLength(doc.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(doc.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(doc.resource());
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID delegationId, @PathVariable UUID documentId) {
        service.delete(delegationId, documentId);
    }
}
