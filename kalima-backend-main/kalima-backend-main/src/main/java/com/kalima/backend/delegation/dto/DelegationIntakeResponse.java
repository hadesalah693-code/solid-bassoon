package com.kalima.backend.delegation.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.delegation.settings.dto.DelegationAttorneyMatchResponse;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Suggested field values parsed from a completed OCR record of a power-of-attorney
 * document. Every field is optional — the user must confirm before saving.
 */
public record DelegationIntakeResponse(
        UUID ocrRecordId,
        String suggestedClientName,
        PartySummary suggestedClient,
        String suggestedAgentName,
        DelegationAttorneyMatchResponse agentMatch,
        String notaryNumber,
        LocalDate issuedAt,
        LocalDate expiresAt,
        DomainEnums.DelegationType delegationType,
        String issuingCountry,
        String textExcerpt
) {
}
