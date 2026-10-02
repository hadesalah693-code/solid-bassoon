package com.kalima.backend.dispute.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DisputeListItemResponse(
        UUID id,
        String disputeNumber,
        String title,
        String opponentName,
        DomainEnums.DisputeType disputeType,
        DomainEnums.DisputeStatus status,
        DomainEnums.LegalPath legalPath,
        DomainEnums.DisputeWorkflowStep currentStep,
        DomainEnums.RiskLevel riskLevel,
        BigDecimal claimAmount,
        PartySummary client,
        PartySummary assignedLawyer,
        UUID convertedCaseId,
        LocalDateTime createdAt
) {
}
