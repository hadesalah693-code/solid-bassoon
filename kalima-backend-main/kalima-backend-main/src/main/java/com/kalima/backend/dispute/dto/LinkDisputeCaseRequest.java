package com.kalima.backend.dispute.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LinkDisputeCaseRequest(
        @NotNull(message = "caseId is required")
        UUID caseId,

        @Size(max = 40, message = "linkRole must be at most 40 characters")
        String linkRole
) {
}
