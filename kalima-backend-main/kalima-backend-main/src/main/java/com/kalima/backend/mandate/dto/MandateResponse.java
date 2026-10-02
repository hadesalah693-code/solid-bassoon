package com.kalima.backend.mandate.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Full detail response for a {@link com.kalima.backend.mandate.entity.Mandate}. */
public record MandateResponse(
        UUID id,
        String mandateNumber,
        int mandateYear,
        DomainEnums.MandateStatus status,
        DomainEnums.MandatePriority priority,
        DomainEnums.MandatePaymentStatus paymentStatus,
        String currency,
        BigDecimal fees,
        BigDecimal amountPaid,
        BigDecimal remaining,
        UUID caseId,
        String caseNumber,
        String caseTitle,
        String courtName,
        UUID hearingId,
        UUID nextHearingId,
        LocalDateTime sessionDate,
        String courtCircuit,
        String courtroom,
        PartySummary client,
        PartySummary originalLawyer,
        PartySummary substitutedLawyer,
        String reason,
        String notes,
        String instructions,
        String cancelledReason,
        String sessionResult,
        String courtDecision,
        Boolean didAttend,
        LocalDateTime nextSessionDate,
        String nextSessionNotes,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
