package com.kalima.backend.delegation.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DelegationListItemResponse(
        UUID id,
        String delegationNumber,
        String notaryNumber,
        String title,
        DomainEnums.DelegationType delegationType,
        DomainEnums.DelegationStatus status,
        String issuingCountry,
        LocalDate issuedAt,
        LocalDate expiresAt,
        Long daysRemaining,
        boolean starred,
        PartySummary client,
        List<PartySummary> agents,
        PartySummary agent,
        LocalDateTime createdAt
) {
}
