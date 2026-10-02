package com.kalima.backend.dispute.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDisputePartyRequest(
        @NotNull(message = "role is required") DomainEnums.DisputePartyRole role,
        UUID personId,
        @Size(max = 250, message = "displayName must be at most 250 characters") String displayName,
        @Size(max = 80, message = "phone must be at most 80 characters") String phone,
        String notes,
        Integer sortOrder
) {
}
