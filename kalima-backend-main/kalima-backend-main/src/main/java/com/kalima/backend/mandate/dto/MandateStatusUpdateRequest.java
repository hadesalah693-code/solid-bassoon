package com.kalima.backend.mandate.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for changing a mandate's status. */
public record MandateStatusUpdateRequest(
        @NotNull(message = "status is required")
        DomainEnums.MandateStatus status,

        @Size(max = 500, message = "cancelledReason must be at most 500 characters")
        String cancelledReason
) {
}
