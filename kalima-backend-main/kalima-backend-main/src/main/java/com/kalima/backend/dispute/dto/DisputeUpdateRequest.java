package com.kalima.backend.dispute.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Partial update: {@code null} fields are left unchanged.
 */
public record DisputeUpdateRequest(
        @Size(max = 250, message = "title must be at most 250 characters")
        String title,

        @Size(max = 250, message = "opponentName must be at most 250 characters")
        String opponentName,

        String description,

        com.kalima.backend.domain.entity.DomainEnums.DisputeType disputeType,

        com.kalima.backend.domain.entity.DomainEnums.DisputeStatus status,

        com.kalima.backend.domain.entity.DomainEnums.LegalPath legalPath,

        com.kalima.backend.domain.entity.DomainEnums.DisputeWorkflowStep currentStep,

        BigDecimal claimAmount,

        com.kalima.backend.domain.entity.DomainEnums.RiskLevel riskLevel,

        @Min(value = 0, message = "successProbability must be between 0 and 100")
        @Max(value = 100, message = "successProbability must be between 0 and 100")
        Integer successProbability,

        UUID clientId,

        com.kalima.backend.domain.entity.DomainEnums.DisputePartyCapacity clientRole,

        com.kalima.backend.domain.entity.DomainEnums.DisputePartyCapacity opponentRole,

        UUID assignedLawyerId
) {
}
