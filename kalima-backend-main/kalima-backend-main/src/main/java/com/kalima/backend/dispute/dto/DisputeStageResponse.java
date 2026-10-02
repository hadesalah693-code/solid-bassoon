package com.kalima.backend.dispute.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record DisputeStageResponse(
        UUID id,
        UUID disputeId,
        String stageType,
        DomainEnums.DisputeStageStatus status,
        String title,
        LocalDateTime startedAt,
        LocalDateTime closedAt,
        DomainEnums.DisputeStageOutcome outcome,
        String outcomeNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
