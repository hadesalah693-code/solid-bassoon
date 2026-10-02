package com.kalima.backend.delegation.intake.controller;

import com.kalima.backend.delegation.dto.DelegationIntakeResponse;
import com.kalima.backend.delegation.intake.service.DelegationIntakeService;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.ocr.dto.OcrRecordResponse;
import com.kalima.backend.ocr.service.OcrService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Delegation OCR intake — upload a scanned POA and parse suggested field values.
 *
 * <p>Upload/process permissions mirror {@code DelegationController#create} so
 * SECRETARY can use the same intake flow as manual delegation creation.
 */
@RestController
@RequestMapping("/api/delegations/intake")
public class DelegationIntakeController {

    private final OcrService ocrService;
    private final DelegationIntakeService intakeService;

    public DelegationIntakeController(OcrService ocrService, DelegationIntakeService intakeService) {
        this.ocrService = ocrService;
        this.intakeService = intakeService;
    }

    @PostMapping(value = "/ocr-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<OcrRecordResponse> uploadForIntake(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "title", required = false) String title,
            UriComponentsBuilder uriBuilder) {
        OcrRecordResponse created = ocrService.upload(
                file, title, DomainEnums.OcrDocumentType.SCAN, null);
        URI location = uriBuilder.path("/api/ocr/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/from-ocr/{ocrRecordId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DelegationIntakeResponse parseFromOcr(@PathVariable UUID ocrRecordId) {
        return intakeService.parseFromOcrRecord(ocrRecordId);
    }

    @PostMapping("/{delegationId}/link-ocr/{ocrRecordId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkOcrToDelegation(
            @PathVariable UUID delegationId,
            @PathVariable UUID ocrRecordId) {
        intakeService.linkOcrRecord(delegationId, ocrRecordId);
    }
}
