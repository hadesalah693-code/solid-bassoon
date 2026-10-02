package com.kalima.backend.criminalreport.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CriminalReportInvestigationSessionResponse(
        UUID id,
        LocalDateTime sessionDate,
        String location,
        String notes
) {
}
