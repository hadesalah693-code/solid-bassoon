package com.kalima.backend.circulation.dto;

import com.kalima.backend.circulation.entity.CirculationEventType;
import java.time.LocalDateTime;
import java.util.UUID;

public record CirculationEventResponse(
        UUID id,
        CirculationEventType eventType,
        UUID actorId,
        String actorName,
        LocalDateTime occurredAt,
        String notes
) {
}