package com.kalima.backend.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteDisputeStageActionRequest(
        @NotBlank(message = "completionNote is required")
        @Size(max = 8000, message = "completionNote must be at most 8000 characters")
        String completionNote
) {
}
