package com.kalima.backend.dispute.controller;

import com.kalima.backend.dispute.dto.CreateDisputePartyRequest;
import com.kalima.backend.dispute.dto.DisputePartyResponse;
import com.kalima.backend.dispute.dto.UpdateDisputePartyRequest;
import com.kalima.backend.dispute.service.DisputePartyService;
import jakarta.validation.Valid;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/disputes/{disputeId}/parties")
public class DisputePartyController {

    private final DisputePartyService disputePartyService;

    public DisputePartyController(DisputePartyService disputePartyService) {
        this.disputePartyService = disputePartyService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DisputePartyResponse> list(@PathVariable UUID disputeId) {
        return disputePartyService.list(disputeId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DisputePartyResponse> create(
            @PathVariable UUID disputeId,
            @Valid @RequestBody CreateDisputePartyRequest request,
            UriComponentsBuilder uriBuilder) {
        DisputePartyResponse created = disputePartyService.create(disputeId, request);
        URI location = uriBuilder
                .path("/api/disputes/{disputeId}/parties/{partyId}")
                .buildAndExpand(disputeId, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{partyId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DisputePartyResponse patch(
            @PathVariable UUID disputeId,
            @PathVariable UUID partyId,
            @Valid @RequestBody UpdateDisputePartyRequest request) {
        return disputePartyService.update(disputeId, partyId, request);
    }

    @DeleteMapping("/{partyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public void delete(@PathVariable UUID disputeId, @PathVariable UUID partyId) {
        disputePartyService.delete(disputeId, partyId);
    }
}
