package com.kalima.backend.criminalreport.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriminalReportOpponentInput(
        @NotBlank(message = "opponent name is required")
        @Size(max = 250, message = "opponent name must be at most 250 characters")
        String name,
        DomainEnums.CriminalReportPartyCapacity capacity
) {
}
