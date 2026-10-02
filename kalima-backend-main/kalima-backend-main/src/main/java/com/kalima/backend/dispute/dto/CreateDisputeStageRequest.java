package com.kalima.backend.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateDisputeStageRequest(
        @NotBlank(message = "stageType is required")
        @Size(max = 50, message = "stageType must be at most 50 characters")
        String stageType,

        @Size(max = 40, message = "status must be at most 40 characters")
        String status,

        @Size(max = 500, message = "title must be at most 500 characters")
        String title,

        LocalDateTime startedAt,
        LocalDateTime closedAt
) {
}
