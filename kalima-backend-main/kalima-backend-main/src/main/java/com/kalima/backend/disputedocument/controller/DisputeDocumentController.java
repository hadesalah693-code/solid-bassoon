package com.kalima.backend.disputedocument.controller;

import com.kalima.backend.disputedocument.dto.DisputeDocumentResponse;
import com.kalima.backend.disputedocument.service.DisputeDocumentService;
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
@RequestMapping("/api/disputes/{disputeId}/documents")
public class DisputeDocumentController {

    private final DisputeDocumentService service;

    public DisputeDocumentController(DisputeDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DisputeDocumentResponse> list(
            @PathVariable UUID disputeId,
            @RequestParam(name = "stageId", required = false) UUID stageId) {
        return service.list(disputeId, stageId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DisputeDocumentResponse> upload(
            @PathVariable UUID disputeId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "category", required = false)
            DomainEnums.CaseDocumentCategory category,
            @RequestParam(name = "visibility", required = false)
            DomainEnums.CaseDocumentVisibility visibility,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "stageId", required = false) UUID stageId,
            UriComponentsBuilder uriBuilder) {
        DisputeDocumentResponse created = service.upload(
                disputeId, stageId, file, category, visibility, description);
        URI location = uriBuilder
                .path("/api/disputes/{disputeId}/documents/{documentId}/download")
                .buildAndExpand(disputeId, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public ResponseEntity<Resource> download(
            @PathVariable UUID disputeId,
            @PathVariable UUID documentId) {
        DisputeDocumentService.DownloadableDocument doc =
                service.download(disputeId, documentId);
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
    public void delete(@PathVariable UUID disputeId, @PathVariable UUID documentId) {
        service.delete(disputeId, documentId);
    }
}
