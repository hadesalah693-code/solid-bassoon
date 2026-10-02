package com.kalima.backend.laborcomplaint.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LaborComplaintMolSessionResponse(
        UUID id,
        LocalDateTime sessionDate,
        String location,
        String notes
) {
}
