package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LaborComplaintStatusUpdateRequest(
        @NotNull(message = "status is required")
        DomainEnums.LaborComplaintStatus status,
        @Size(max = 5000, message = "savedReason must be at most 5000 characters")
        String savedReason,
        @Size(max = 5000, message = "referralNotes must be at most 5000 characters")
        String referralNotes,
        UUID linkedCaseId
) {
}
