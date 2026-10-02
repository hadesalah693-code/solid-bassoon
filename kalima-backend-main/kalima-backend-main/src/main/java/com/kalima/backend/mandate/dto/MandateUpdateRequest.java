package com.kalima.backend.mandate.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Request body for updating a {@link com.kalima.backend.mandate.entity.Mandate}. */
public record MandateUpdateRequest(
        UUID caseId,
        UUID hearingId,
        UUID nextHearingId,
        UUID originalLawyerId,
        UUID substitutedLawyerId,
        DomainEnums.MandatePriority priority,
        DomainEnums.MandateStatus status,
        LocalDateTime sessionDate,
        @Size(max = 200) String courtName,
        @Size(max = 100) String courtCircuit,
        @Size(max = 100) String courtroom,
        String reason,
        String notes,
        String instructions,
        @DecimalMin(value = "0.0", message = "fees cannot be negative") BigDecimal fees,
        @Size(min = 3, max = 3, message = "currency must be a 3-letter code") String currency,
        @DecimalMin(value = "0.0", message = "amountPaid cannot be negative") BigDecimal amountPaid,
        @Size(max = 500) String cancelledReason
) {
        public MandateUpdateRequest {
                if (originalLawyerId != null
                        && originalLawyerId.equals(substitutedLawyerId)) {
                        throw new IllegalArgumentException(
                                "originalLawyerId and substitutedLawyerId must be different");
                }
        }
}
