package com.kalima.backend.dispute.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DisputeTeamMemberAssignment(
        @NotNull(message = "role is required")
        DomainEnums.DisputeTeamRole role,
        UUID personId
) {
}
