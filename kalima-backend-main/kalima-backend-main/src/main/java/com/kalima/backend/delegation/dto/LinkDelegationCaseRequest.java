package com.kalima.backend.delegation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkDelegationCaseRequest(
        @NotNull(message = "caseId is required")
        UUID caseId
) {
}
