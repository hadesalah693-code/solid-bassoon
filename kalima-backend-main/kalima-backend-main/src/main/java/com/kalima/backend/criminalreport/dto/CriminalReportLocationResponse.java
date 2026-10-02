package com.kalima.backend.criminalreport.dto;

import java.util.UUID;

public record CriminalReportLocationResponse(
        UUID id,
        String locationType,
        String locationDescription
) {
}
