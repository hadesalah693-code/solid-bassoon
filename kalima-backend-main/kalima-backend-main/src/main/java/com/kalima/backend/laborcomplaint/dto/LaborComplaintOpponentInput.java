package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LaborComplaintOpponentInput(
        @NotBlank(message = "opponent name is required")
        @Size(max = 250, message = "opponent name must be at most 250 characters")
        String name,
        DomainEnums.LaborComplaintPartyCapacity capacity
) {
}
