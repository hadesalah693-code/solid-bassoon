package com.kalima.backend.dispute.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DisputeResponse(
        UUID id,
        String disputeNumber,
        String opponentName,
        DomainEnums.DisputePartyCapacity opponentRole,
        String title,
        String description,
        DomainEnums.DisputeType disputeType,
        DomainEnums.DisputeStatus status,
        DomainEnums.LegalPath legalPath,
        DomainEnums.DisputeWorkflowStep currentStep,
        BigDecimal claimAmount,
        DomainEnums.RiskLevel riskLevel,
        Integer successProbability,
        PartySummary client,
        DomainEnums.DisputePartyCapacity clientRole,
        PartySummary assignedLawyer,
        UUID convertedCaseId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<DisputeTimelineEventResponse> timeline,
        List<DisputeTeamMemberResponse> team
) {
}
