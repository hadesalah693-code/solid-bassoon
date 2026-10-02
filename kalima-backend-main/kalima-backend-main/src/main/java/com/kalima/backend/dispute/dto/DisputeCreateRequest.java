package com.kalima.backend.dispute.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record DisputeCreateRequest(
        @NotBlank(message = "title is required")
        @Size(max = 250, message = "title must be at most 250 characters")
        String title,

        @NotBlank(message = "opponentName is required")
        @Size(max = 250, message = "opponentName must be at most 250 characters")
        String opponentName,

        String description,

        @NotNull(message = "disputeType is required")
        com.kalima.backend.domain.entity.DomainEnums.DisputeType disputeType,

        @NotNull(message = "riskLevel is required")
        com.kalima.backend.domain.entity.DomainEnums.RiskLevel riskLevel,

        com.kalima.backend.domain.entity.DomainEnums.LegalPath legalPath,

        BigDecimal claimAmount,

        @Min(value = 0, message = "successProbability must be between 0 and 100")
        @Max(value = 100, message = "successProbability must be between 0 and 100")
        Integer successProbability,

        @NotNull(message = "clientId is required")
        UUID clientId,

        com.kalima.backend.domain.entity.DomainEnums.DisputePartyCapacity clientRole,

        com.kalima.backend.domain.entity.DomainEnums.DisputePartyCapacity opponentRole,

        UUID assignedLawyerId,

        UUID administrativeOfficerId
) {
}
