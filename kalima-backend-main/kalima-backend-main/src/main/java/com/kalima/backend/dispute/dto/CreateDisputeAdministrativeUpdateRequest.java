package com.kalima.backend.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateDisputeAdministrativeUpdateRequest(
        @NotBlank(message = "updateType is required")
        @Size(max = 40, message = "updateType must be at most 40 characters")
        String updateType,

        @NotBlank(message = "title is required")
        @Size(max = 500, message = "title must be at most 500 characters")
        String title,

        String description,

        LocalDateTime occurredAt,

        UUID stageId
) {
}
