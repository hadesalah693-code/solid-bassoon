package com.kalima.backend.dispute.controller;

import com.kalima.backend.dispute.dto.DisputeTeamMemberResponse;
import com.kalima.backend.dispute.dto.UpdateDisputeTeamRequest;
import com.kalima.backend.dispute.service.DisputeTeamService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/disputes/{disputeId}/team")
public class DisputeTeamController {

    private final DisputeTeamService disputeTeamService;

    public DisputeTeamController(DisputeTeamService disputeTeamService) {
        this.disputeTeamService = disputeTeamService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DisputeTeamMemberResponse> list(@PathVariable UUID disputeId) {
        return disputeTeamService.list(disputeId);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public List<DisputeTeamMemberResponse> updateTeam(
            @PathVariable UUID disputeId,
            @Valid @RequestBody UpdateDisputeTeamRequest request) {
        return disputeTeamService.updateTeam(disputeId, request);
    }
}
