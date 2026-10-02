package com.kalima.backend.criminalreport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriminalReportLocationInput(
        @Size(max = 100, message = "locationType must be at most 100 characters")
        String locationType,
        @Size(max = 2000, message = "locationDescription must be at most 2000 characters")
        String locationDescription
) {
}
