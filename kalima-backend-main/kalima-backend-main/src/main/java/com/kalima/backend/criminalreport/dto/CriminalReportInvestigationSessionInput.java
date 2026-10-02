package com.kalima.backend.criminalreport.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CriminalReportInvestigationSessionInput(
        @NotNull(message = "sessionDate is required")
        LocalDateTime sessionDate,
        @Size(max = 250, message = "location must be at most 250 characters")
        String location,
        @Size(max = 5000, message = "notes must be at most 5000 characters")
        String notes
) {
}
