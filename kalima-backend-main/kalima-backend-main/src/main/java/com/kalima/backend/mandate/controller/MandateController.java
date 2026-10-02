package com.kalima.backend.mandate.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.mandate.dto.MandateCreateRequest;
import com.kalima.backend.mandate.dto.MandateEventResponse;
import com.kalima.backend.mandate.dto.MandateListItemResponse;
import com.kalima.backend.mandate.dto.MandatePaymentRequest;
import com.kalima.backend.mandate.dto.MandatePaymentResponse;
import com.kalima.backend.mandate.dto.MandateResponse;
import com.kalima.backend.mandate.dto.MandateSessionResultRequest;
import com.kalima.backend.mandate.dto.MandateStatisticsResponse;
import com.kalima.backend.mandate.dto.MandateStatusUpdateRequest;
import com.kalima.backend.mandate.dto.MandateUpdateRequest;
import com.kalima.backend.mandate.service.MandateService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mandates")
public class MandateController {

    private final MandateService mandateService;

    public MandateController(MandateService mandateService) {
        this.mandateService = mandateService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<MandateResponse> create(
            @Valid @RequestBody MandateCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        MandateResponse created = mandateService.create(request);
        URI location = uriBuilder.path("/api/mandates/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public MandateStatisticsResponse statistics() {
        return mandateService.statistics();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<MandateListItemResponse> list(
            @RequestParam(name = "status", required = false) DomainEnums.MandateStatus status,
            @RequestParam(name = "priority", required = false) DomainEnums.MandatePriority priority,
            @RequestParam(name = "paymentStatus", required = false)
            DomainEnums.MandatePaymentStatus paymentStatus,
            @RequestParam(name = "caseId", required = false) UUID caseId,
            @RequestParam(name = "originalLawyerId", required = false) UUID originalLawyerId,
            @RequestParam(name = "substitutedLawyerId", required = false) UUID substitutedLawyerId,
            @RequestParam(name = "courtName", required = false) String courtName,
            @RequestParam(name = "sessionFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sessionFrom,
            @RequestParam(name = "sessionTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sessionTo,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return mandateService.search(
                status, priority, paymentStatus, caseId, originalLawyerId,
                substitutedLawyerId, courtName, sessionFrom, sessionTo, search, pageable);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public ResponseEntity<byte[]> export(
            @RequestParam(name = "status", required = false) DomainEnums.MandateStatus status,
            @RequestParam(name = "priority", required = false) DomainEnums.MandatePriority priority,
            @RequestParam(name = "paymentStatus", required = false)
            DomainEnums.MandatePaymentStatus paymentStatus,
            @RequestParam(name = "caseId", required = false) UUID caseId,
            @RequestParam(name = "originalLawyerId", required = false) UUID originalLawyerId,
            @RequestParam(name = "substitutedLawyerId", required = false) UUID substitutedLawyerId,
            @RequestParam(name = "courtName", required = false) String courtName,
            @RequestParam(name = "sessionFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sessionFrom,
            @RequestParam(name = "sessionTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime sessionTo,
            @RequestParam(name = "search", required = false) String search) {
        byte[] csv = mandateService.exportCsv(
                status, priority, paymentStatus, caseId, originalLawyerId,
                substitutedLawyerId, courtName, sessionFrom, sessionTo, search);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mandates.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public MandateResponse getById(@PathVariable UUID id) {
        return mandateService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public MandateResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody MandateUpdateRequest request) {
        return mandateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        mandateService.delete(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public MandateResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody MandateStatusUpdateRequest request) {
        return mandateService.changeStatus(id, request);
    }

    @PostMapping("/{id}/session-result")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public MandateResponse recordSessionResult(
            @PathVariable UUID id,
            @Valid @RequestBody MandateSessionResultRequest request) {
        return mandateService.recordSessionResult(id, request);
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<MandatePaymentResponse> listPayments(@PathVariable UUID id) {
        return mandateService.listPayments(id);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','ACCOUNTANT')")
    public ResponseEntity<MandatePaymentResponse> recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody MandatePaymentRequest request,
            UriComponentsBuilder uriBuilder) {
        MandatePaymentResponse created = mandateService.recordPayment(id, request);
        URI location = uriBuilder.path("/api/mandates/{id}/payments")
                .buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<MandateEventResponse> timeline(@PathVariable UUID id) {
        return mandateService.listTimeline(id);
    }
}
