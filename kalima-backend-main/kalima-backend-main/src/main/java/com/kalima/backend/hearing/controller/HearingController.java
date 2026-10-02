package com.kalima.backend.hearing.controller;

import com.kalima.backend.hearing.dto.HearingCreateRequest;
import com.kalima.backend.hearing.dto.HearingResponse;
import com.kalima.backend.hearing.dto.HearingStatusUpdateRequest;
import com.kalima.backend.hearing.dto.HearingUpdateRequest;
import com.kalima.backend.hearing.entity.HearingStatus;
import com.kalima.backend.hearing.entity.HearingType;
import com.kalima.backend.hearing.service.HearingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/hearings")
public class HearingController {

    private final HearingService hearingService;

    public HearingController(HearingService hearingService) {
        this.hearingService = hearingService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<HearingResponse> create(
            @PathVariable UUID caseId,
            @Valid @RequestBody HearingCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        HearingResponse created = hearingService.create(caseId, request);
        URI loc = uriBuilder
                .path("/api/cases/{caseId}/hearings/{hearingId}")
                .buildAndExpand(caseId, created.id())
                .toUri();
        return ResponseEntity.created(loc).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public Page<HearingResponse> list(
            @PathVariable UUID caseId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) HearingStatus status,
            @RequestParam(required = false) HearingType type,
            @RequestParam(required = false) UUID lawyerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return hearingService.list(caseId, from, to, status, type, lawyerId, pageable);
    }

    @GetMapping("/{hearingId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public HearingResponse get(
            @PathVariable UUID caseId,
            @PathVariable UUID hearingId) {
        return hearingService.findById(caseId, hearingId);
    }

    @PutMapping("/{hearingId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    @ResponseStatus(HttpStatus.OK)
    public HearingResponse update(
            @PathVariable UUID caseId,
            @PathVariable UUID hearingId,
            @Valid @RequestBody HearingUpdateRequest request) {
        return hearingService.update(caseId, hearingId, request);
    }

    @PatchMapping("/{hearingId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public HearingResponse updateStatus(
            @PathVariable UUID caseId,
            @PathVariable UUID hearingId,
            @Valid @RequestBody HearingStatusUpdateRequest request) {
        return hearingService.updateStatus(caseId, hearingId, request);
    }

    @DeleteMapping("/{hearingId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID caseId, @PathVariable UUID hearingId) {
        hearingService.delete(caseId, hearingId);
    }
}
