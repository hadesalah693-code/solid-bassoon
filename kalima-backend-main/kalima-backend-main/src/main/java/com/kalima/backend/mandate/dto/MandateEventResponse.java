package com.kalima.backend.mandate.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

/** Response for a mandate timeline event. */
public record MandateEventResponse(
        UUID id,
        DomainEnums.MandateEventType eventType,
        String note,
        String actorEmail,
        LocalDateTime createdAt
) {
}
