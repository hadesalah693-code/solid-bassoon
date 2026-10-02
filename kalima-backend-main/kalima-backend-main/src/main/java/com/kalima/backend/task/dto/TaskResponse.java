package com.kalima.backend.task.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID caseId,
        UUID disputeId,
        String caseNumber,
        String caseTitle,
        String disputeNumber,
        String disputeTitle,
        String title,
        String description,
        DomainEnums.TaskPriority priority,
        DomainEnums.TaskStatus status,
        LocalDateTime dueDate,
        PartySummary assignedTo,
        PartySummary createdBy,
        DomainEnums.AdminTaskType adminTaskType,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
