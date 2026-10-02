package com.kalima.backend.criminalreport.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CriminalReportClientInput(
        @NotNull(message = "clientId is required")
        UUID clientId,
        DomainEnums.CriminalReportPartyCapacity capacity
) {
}
