package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LaborComplaintClientInput(
        @NotNull(message = "clientId is required")
        UUID clientId,
        DomainEnums.LaborComplaintPartyCapacity capacity
) {
}
