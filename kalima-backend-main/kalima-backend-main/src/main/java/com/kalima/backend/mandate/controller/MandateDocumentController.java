package com.kalima.backend.mandate.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.mandate.dto.MandateDocumentResponse;
import com.kalima.backend.mandate.service.MandateDocumentService;
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
@RequestMapping("/api/mandates/{mandateId}/documents")
public class MandateDocumentController {

    private final MandateDocumentService service;

    public MandateDocumentController(MandateDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<MandateDocumentResponse> list(@PathVariable UUID mandateId) {
        return service.list(mandateId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<MandateDocumentResponse> upload(
            @PathVariable UUID mandateId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "documentType", required = false)
            DomainEnums.MandateDocumentType documentType,
            UriComponentsBuilder uriBuilder) {
        MandateDocumentResponse created = service.upload(mandateId, file, description, documentType);
        URI location = uriBuilder
                .path("/api/mandates/{mandateId}/documents/{documentId}/download")
                .buildAndExpand(mandateId, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public ResponseEntity<Resource> download(
            @PathVariable UUID mandateId,
            @PathVariable UUID documentId) {
        MandateDocumentService.DownloadableDocument doc =
                service.download(mandateId, documentId);
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
    public void delete(@PathVariable UUID mandateId, @PathVariable UUID documentId) {
        service.delete(mandateId, documentId);
    }
}
