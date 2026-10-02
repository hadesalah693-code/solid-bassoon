package com.kalima.backend.mandate.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Lightweight read-only row for the mandates list. */
public record MandateListItemResponse(
        UUID id,
        String mandateNumber,
        DomainEnums.MandateStatus status,
        DomainEnums.MandatePriority priority,
        DomainEnums.MandatePaymentStatus paymentStatus,
        String caseNumber,
        String caseTitle,
        PartySummary client,
        PartySummary originalLawyer,
        PartySummary substitutedLawyer,
        LocalDateTime sessionDate,
        String courtName,
        BigDecimal fees,
        String currency,
        LocalDateTime createdAt
) {
        public MandateListItemResponse {
        }
}
