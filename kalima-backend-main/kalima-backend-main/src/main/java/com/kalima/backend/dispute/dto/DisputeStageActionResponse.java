package com.kalima.backend.dispute.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record DisputeStageActionResponse(
        UUID id,
        UUID disputeId,
        UUID stageId,
        DomainEnums.DisputeActionType actionType,
        String title,
        String description,
        DomainEnums.DisputeActionStatus status,
        DomainEnums.DisputeActionPriority priority,
        LocalDateTime dueAt,
        LocalDateTime completedAt,
        String completionNote,
        PartySummary completedBy,
        PartySummary assignedTo,
        PartySummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean overdue
) {
}
