package com.kalima.backend.dispute.dto;

import com.kalima.backend.casefile.dto.PartySummary;

import java.time.LocalDateTime;
import java.util.UUID;

public record DisputeAdministrativeUpdateResponse(
        UUID id,
        UUID disputeId,
        UUID stageId,
        String updateType,
        String title,
        String description,
        LocalDateTime occurredAt,
        PartySummary createdBy,
        LocalDateTime createdAt
) {
}
