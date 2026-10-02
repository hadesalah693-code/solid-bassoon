package com.kalima.backend.delegation.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DelegationUpdateRequest(
        UUID agentId,

        List<UUID> agentIds,

        DomainEnums.DelegationType delegationType,

        @Size(min = 2, max = 2, message = "issuingCountry must be ISO 3166-1 alpha-2")
        String issuingCountry,

        DomainEnums.DelegationStatus status,

        DomainEnums.DelegationCancelReason cancelReason,

        @Size(max = 500, message = "cancelReasonNote must be at most 500 characters")
        String cancelReasonNote,

        LocalDate cancelledAt,

        LocalDate cancellationAnnouncementAt,

        LocalDate issuedAt,

        LocalDate expiresAt,

        @Size(max = 50, message = "notaryNumber must be at most 50 characters")
        String notaryNumber,

        List<UUID> partnerIds,

        String notes,

        List<Integer> reminderOffsets,

        List<UUID> reminderRecipientIds,

        Boolean starred
) {
}
