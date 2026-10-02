package com.kalima.backend.delegation.settings.dto;

import com.kalima.backend.casefile.dto.PartySummary;

import java.time.LocalDateTime;
import java.util.UUID;

public record DelegationOfficeAttorneyResponse(
        UUID id,
        PartySummary person,
        boolean active,
        boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
