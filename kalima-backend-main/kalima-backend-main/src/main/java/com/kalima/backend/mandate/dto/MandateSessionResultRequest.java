package com.kalima.backend.mandate.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/** Request body for recording the outcome of a mandate's court session. */
public record MandateSessionResultRequest(
        @NotNull(message = "didAttend is required")
        Boolean didAttend,

        String sessionResult,
        String courtDecision,
        String notes,

        UUID nextSessionCaseId,
        UUID nextSessionHearingId,
        LocalDateTime nextSessionDate,
        String nextSessionNotes
) {
}
