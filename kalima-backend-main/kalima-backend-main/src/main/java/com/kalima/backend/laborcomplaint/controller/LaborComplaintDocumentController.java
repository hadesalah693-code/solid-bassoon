package com.kalima.backend.laborcomplaint.controller;

import com.kalima.backend.laborcomplaint.dto.LaborComplaintDocumentResponse;
import com.kalima.backend.laborcomplaint.service.LaborComplaintDocumentService;
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
@RequestMapping("/api/labor-complaints/{laborComplaintId}/documents")
public class LaborComplaintDocumentController {

    private final LaborComplaintDocumentService service;

    public LaborComplaintDocumentController(LaborComplaintDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<LaborComplaintDocumentResponse> list(@PathVariable UUID laborComplaintId) {
        return service.list(laborComplaintId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<LaborComplaintDocumentResponse> upload(
            @PathVariable UUID laborComplaintId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "description", required = false) String description,
            UriComponentsBuilder uriBuilder) {
        LaborComplaintDocumentResponse created = service.upload(laborComplaintId, file, description);
        URI location = uriBuilder
                .path("/api/labor-complaints/{laborComplaintId}/documents/{documentId}/download")
                .buildAndExpand(laborComplaintId, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public ResponseEntity<Resource> download(
            @PathVariable UUID laborComplaintId,
            @PathVariable UUID documentId) {
        LaborComplaintDocumentService.DownloadableDocument doc = service.download(laborComplaintId, documentId);
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
    public void delete(@PathVariable UUID laborComplaintId, @PathVariable UUID documentId) {
        service.delete(laborComplaintId, documentId);
    }
}
