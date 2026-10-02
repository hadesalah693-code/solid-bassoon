package com.kalima.backend.delegation.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DelegationResponse(
        UUID id,
        String delegationNumber,
        String notaryNumber,
        String title,
        DomainEnums.DelegationType delegationType,
        DomainEnums.DelegationStatus status,
        String issuingCountry,
        DomainEnums.DelegationCancelReason cancelReason,
        String cancelReasonNote,
        LocalDate cancelledAt,
        LocalDate cancellationAnnouncementAt,
        LocalDate issuedAt,
        LocalDate expiresAt,
        Long daysRemaining,
        String notes,
        List<Integer> reminderOffsets,
        List<UUID> reminderRecipientIds,
        boolean starred,
        PartySummary client,
        List<PartySummary> partners,
        List<PartySummary> agents,
        PartySummary agent,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
