package com.kalima.backend.disputeevent.controller;

import com.kalima.backend.disputeevent.dto.DisputeEventCreateRequest;
import com.kalima.backend.disputeevent.dto.DisputeEventPatchRequest;
import com.kalima.backend.disputeevent.dto.DisputeEventResponse;
import com.kalima.backend.disputeevent.service.DisputeEventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/dispute-events")
public class DisputeEventController {

    private final DisputeEventService disputeEventService;

    public DisputeEventController(DisputeEventService disputeEventService) {
        this.disputeEventService = disputeEventService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DisputeEventResponse> create(
            @PathVariable UUID caseId,
            @Valid @RequestBody DisputeEventCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        DisputeEventResponse created = disputeEventService.create(caseId, request);
        URI loc = uriBuilder
                .path("/api/cases/{caseId}/dispute-events/{eventId}")
                .buildAndExpand(caseId, created.id())
                .toUri();
        return ResponseEntity.created(loc).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public Page<DisputeEventResponse> list(
            @PathVariable UUID caseId,
            @PageableDefault(size = 20) Pageable pageable) {
        return disputeEventService.list(caseId, pageable);
    }

    @PatchMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    @ResponseStatus(HttpStatus.OK)
    public DisputeEventResponse update(
            @PathVariable UUID caseId,
            @PathVariable UUID eventId,
            @Valid @RequestBody DisputeEventPatchRequest request) {
        return disputeEventService.update(caseId, eventId, request);
    }

    @DeleteMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID caseId,
            @PathVariable UUID eventId) {
        disputeEventService.delete(caseId, eventId);
    }
}
