package com.kalima.backend.dispute.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Partial update: {@code null} fields are left unchanged.
 */
public record UpdateDisputeStageRequest(
        @Size(max = 500, message = "title must be at most 500 characters")
        String title,

        DomainEnums.DisputeStageStatus status,

        LocalDateTime startedAt,
        LocalDateTime closedAt,

        /** Required when the stage becomes closed (first-time closure). */
        @Size(max = 8000, message = "closingNote must be at most 8000 characters")
        String closingNote,

        /** Required when the stage becomes closed (first-time closure). */
        DomainEnums.DisputeStageOutcome outcome,

        @Size(max = 8000, message = "outcomeNote must be at most 8000 characters")
        String outcomeNote
) {
}
