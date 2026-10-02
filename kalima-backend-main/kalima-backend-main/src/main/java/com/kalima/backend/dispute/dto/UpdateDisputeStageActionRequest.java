package com.kalima.backend.dispute.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateDisputeStageActionRequest(
        Boolean unlinkStage,

        UUID stageId,

        DomainEnums.DisputeActionType actionType,

        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        String description,

        DomainEnums.DisputeActionStatus status,

        DomainEnums.DisputeActionPriority priority,

        LocalDateTime dueAt,

        Boolean clearDueAt,

        UUID assignedTo,

        Boolean clearAssignee,

        /** Required when transitioning status to DONE via PATCH. */
        @Size(max = 8000, message = "completionNote must be at most 8000 characters")
        String completionNote
) {
}
