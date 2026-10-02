package com.kalima.backend.delegation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DelegationCreateRequest(
        @NotNull(message = "clientId is required")
        UUID clientId,

        UUID agentId,

        List<UUID> agentIds,

        @NotNull(message = "delegationType is required")
        com.kalima.backend.domain.entity.DomainEnums.DelegationType delegationType,

        @NotBlank(message = "issuingCountry is required")
        @Size(min = 2, max = 2, message = "issuingCountry must be ISO 3166-1 alpha-2")
        String issuingCountry,

        @NotNull(message = "issuedAt is required")
        LocalDate issuedAt,

        LocalDate expiresAt,

        @Size(max = 50, message = "notaryNumber must be at most 50 characters")
        String notaryNumber,

        List<UUID> partnerIds,

        String notes,

        List<Integer> reminderOffsets,

        List<UUID> reminderRecipientIds
) {
}
