package com.kalima.backend.dispute.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateDisputeStageActionRequest(
        UUID stageId,

        @NotNull(message = "actionType is required")
        DomainEnums.DisputeActionType actionType,

        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        String description,

        DomainEnums.DisputeActionPriority priority,

        LocalDateTime dueAt,

        UUID assignedTo
) {
}
