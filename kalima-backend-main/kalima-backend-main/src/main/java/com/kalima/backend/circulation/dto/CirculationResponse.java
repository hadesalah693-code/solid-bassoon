package com.kalima.backend.circulation.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.circulation.entity.CirculationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CirculationResponse(
        UUID id,
        UUID caseId,
        String caseNumber,
        String caseTitle,
        PartySummary fromPerson,
        PartySummary toPerson,
        PartySummary requestedBy,
        PartySummary approvedBy,
        LocalDateTime requestedAt,
        LocalDateTime dueBackAt,
        LocalDateTime handedAt,
        LocalDateTime returnedAt,
        CirculationStatus status,
        String reason,
        String notes,
        List<CirculationEventResponse> events
) {
}