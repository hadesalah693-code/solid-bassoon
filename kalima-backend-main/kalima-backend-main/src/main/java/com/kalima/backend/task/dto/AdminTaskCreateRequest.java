package com.kalima.backend.task.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tenant-wide task creation — optional links to a case and/or dispute.
 */
public record AdminTaskCreateRequest(
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        String description,

        @NotNull(message = "priority is required")
        DomainEnums.TaskPriority priority,

        @NotNull(message = "status is required")
        DomainEnums.TaskStatus status,

        LocalDateTime dueDate,

        UUID assignedToId,

        UUID caseId,

        UUID disputeId,

        DomainEnums.AdminTaskType adminTaskType
) {
}
