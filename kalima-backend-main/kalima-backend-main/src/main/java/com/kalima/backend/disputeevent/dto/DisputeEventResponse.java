package com.kalima.backend.disputeevent.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record DisputeEventResponse(
        UUID id,
        UUID caseId,
        DomainEnums.DisputeEventType type,
        DomainEnums.DisputeEventStatus status,
        String title,
        String summary,
        LocalDateTime eventDate,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
