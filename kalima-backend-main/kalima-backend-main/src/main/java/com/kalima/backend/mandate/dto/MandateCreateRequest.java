package com.kalima.backend.mandate.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Request body for creating a {@link com.kalima.backend.mandate.entity.Mandate}. */
public record MandateCreateRequest(
        @NotNull(message = "caseId is required")
        UUID caseId,

        UUID hearingId,

        UUID nextHearingId,

        @NotNull(message = "clientId is required")
        UUID clientId,

        @NotNull(message = "originalLawyerId is required")
        UUID originalLawyerId,

        @NotNull(message = "substitutedLawyerId is required")
        UUID substitutedLawyerId,

        @NotNull(message = "priority is required")
        DomainEnums.MandatePriority priority,

        LocalDateTime sessionDate,

        @Size(max = 200, message = "courtName must be at most 200 characters")
        String courtName,

        @Size(max = 100, message = "courtCircuit must be at most 100 characters")
        String courtCircuit,

        @Size(max = 100, message = "courtroom must be at most 100 characters")
        String courtroom,

        String reason,
        String notes,
        String instructions,

        @DecimalMin(value = "0.0", message = "fees cannot be negative")
        BigDecimal fees,

        @Size(min = 3, max = 3, message = "currency must be a 3-letter code")
        String currency,

        @DecimalMin(value = "0.0", message = "amountPaid cannot be negative")
        BigDecimal amountPaid
) {
        public MandateCreateRequest {
                if (originalLawyerId != null && originalLawyerId.equals(substitutedLawyerId)) {
                        throw new IllegalArgumentException(
                                "originalLawyerId and substitutedLawyerId must be different");
                }
        }
}
