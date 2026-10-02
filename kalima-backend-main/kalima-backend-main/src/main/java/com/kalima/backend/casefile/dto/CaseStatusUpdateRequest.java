package com.kalima.backend.casefile.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;

public record CaseStatusUpdateRequest(
        @NotNull(message = "status is required")
        DomainEnums.CaseStatus status
) {
}
