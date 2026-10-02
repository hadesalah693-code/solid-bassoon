package com.kalima.backend.dispute.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateDisputeTeamRequest(
        @NotEmpty(message = "assignments must not be empty")
        @Valid
        List<DisputeTeamMemberAssignment> assignments
) {
}
