package com.kalima.backend.ocr.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.ocr.dto.OcrRecordListResponse;
import com.kalima.backend.ocr.dto.OcrRecordResponse;
import com.kalima.backend.ocr.dto.OcrRecordUpdateRequest;
import com.kalima.backend.ocr.dto.OcrSearchParams;
import com.kalima.backend.ocr.dto.OcrStatsResponse;
import com.kalima.backend.ocr.service.OcrService;
import com.kalima.backend.ocr.service.OcrStatsService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final OcrService ocrService;
    private final OcrStatsService statsService;

    public OcrController(OcrService ocrService, OcrStatsService statsService) {
        this.ocrService = ocrService;
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public OcrStatsResponse stats() {
        return statsService.stats();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER')")
    public ResponseEntity<OcrRecordResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "documentType", required = false)
            DomainEnums.OcrDocumentType documentType,
            @RequestParam(name = "relations", required = false) String relationsJson,
            UriComponentsBuilder uriBuilder) {
        OcrRecordResponse created = ocrService.upload(file, title, documentType, relationsJson);
        URI location = uriBuilder.path("/api/ocr/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<OcrRecordListResponse> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) DomainEnums.OcrStatus status,
            @RequestParam(name = "documentType", required = false)
            DomainEnums.OcrDocumentType documentType,
            @RequestParam(name = "uploadedBy", required = false) UUID uploadedBy,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "caseId", required = false) UUID caseId,
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "disputeId", required = false) UUID disputeId,
            @RequestParam(name = "taskId", required = false) UUID taskId,
            @RequestParam(name = "documentId", required = false) UUID documentId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        OcrSearchParams params = new OcrSearchParams(
                q, status, documentType, uploadedBy, from, to,
                caseId, clientId, disputeId, taskId, documentId);
        return ocrService.search(params, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public OcrRecordResponse get(@PathVariable UUID id) {
        return ocrService.get(id, true);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER')")
    public OcrRecordResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody OcrRecordUpdateRequest request) {
        return ocrService.update(id, request);
    }

    @PostMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER')")
    public OcrRecordResponse process(
            @PathVariable UUID id,
            @RequestParam(name = "rerun", defaultValue = "false") boolean rerun) {
        return ocrService.requestProcess(id, rerun);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        ocrService.delete(id);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public ResponseEntity<Resource> downloadOriginal(@PathVariable UUID id) {
        OcrService.DownloadableFile file = ocrService.downloadOriginal(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(file.resource());
    }

    @GetMapping("/{id}/text/download")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public ResponseEntity<Resource> downloadText(@PathVariable UUID id) {
        OcrService.DownloadableText text = ocrService.downloadText(id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .contentLength(text.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(text.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new ByteArrayResource(text.bytes()));
    }
}
