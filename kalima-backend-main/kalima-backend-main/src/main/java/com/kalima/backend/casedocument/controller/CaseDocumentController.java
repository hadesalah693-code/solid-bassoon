package com.kalima.backend.casedocument.controller;

import com.kalima.backend.casedocument.dto.CaseDocumentResponse;
import com.kalima.backend.casedocument.service.CaseDocumentService;
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
@RequestMapping("/api/cases/{caseId}/documents")
public class CaseDocumentController {

    private final CaseDocumentService service;

    public CaseDocumentController(CaseDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public List<CaseDocumentResponse> list(@PathVariable UUID caseId) {
        return service.list(caseId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<CaseDocumentResponse> upload(
            @PathVariable UUID caseId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "category", required = false)
            DomainEnums.CaseDocumentCategory category,
            @RequestParam(name = "visibility", required = false)
            DomainEnums.CaseDocumentVisibility visibility,
            @RequestParam(name = "description", required = false) String description,
            UriComponentsBuilder uriBuilder) {
        CaseDocumentResponse created = service.upload(
                caseId, file, category, visibility, description);
        URI location = uriBuilder
                .path("/api/cases/{caseId}/documents/{documentId}/download")
                .buildAndExpand(caseId, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public ResponseEntity<Resource> download(
            @PathVariable UUID caseId,
            @PathVariable UUID documentId) {
        CaseDocumentService.DownloadableDocument doc = service.download(caseId, documentId);
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
    public void delete(@PathVariable UUID caseId, @PathVariable UUID documentId) {
        service.delete(caseId, documentId);
    }
}
