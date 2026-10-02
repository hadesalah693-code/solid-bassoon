package com.kalima.backend.circulation.controller;

import com.kalima.backend.circulation.dto.CirculationActionRequest;
import com.kalima.backend.circulation.dto.CirculationCreateRequest;
import com.kalima.backend.circulation.dto.CirculationResponse;
import com.kalima.backend.circulation.entity.CirculationStatus;
import com.kalima.backend.circulation.service.CirculationRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/circulation-requests")
@PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
public class CirculationRequestController {

    private final CirculationRequestService service;

    public CirculationRequestController(CirculationRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CirculationResponse> create(@Valid @RequestBody CirculationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/inbox")
    public Page<CirculationResponse> inbox(
            @RequestParam(required = false) CirculationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.inbox(status, pageable);
    }

    @GetMapping("/outbox")
    public Page<CirculationResponse> outbox(
            @RequestParam(required = false) CirculationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.outbox(status, pageable);
    }

    @GetMapping("/case/{caseId}")
    public Page<CirculationResponse> forCase(
            @PathVariable UUID caseId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.forCase(caseId, pageable);
    }

    @GetMapping("/{id}")
    public CirculationResponse get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    public CirculationResponse approve(@PathVariable UUID id,
            @Valid @RequestBody(required = false) CirculationActionRequest request) {
        return service.approve(id, request == null ? null : request.notes());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    public CirculationResponse reject(@PathVariable UUID id,
            @Valid @RequestBody(required = false) CirculationActionRequest request) {
        return service.reject(id, request == null ? null : request.notes());
    }

    @PostMapping("/{id}/handover")
    public CirculationResponse handover(@PathVariable UUID id,
            @Valid @RequestBody(required = false) CirculationActionRequest request) {
        return service.handover(id, request == null ? null : request.notes());
    }

    @PostMapping("/{id}/return")
    public CirculationResponse returnFile(@PathVariable UUID id,
            @Valid @RequestBody(required = false) CirculationActionRequest request) {
        return service.returnFile(id, request == null ? null : request.notes());
    }

    @PostMapping("/{id}/cancel")
    public CirculationResponse cancel(@PathVariable UUID id,
            @Valid @RequestBody(required = false) CirculationActionRequest request) {
        return service.cancel(id, request == null ? null : request.notes());
    }
}