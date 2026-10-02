package com.kalima.backend.dispute.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DisputeTimelineEventResponse(
        UUID id,
        DomainEnums.DisputeTimelineEventType eventType,
        String title,
        String description,
        LocalDateTime createdAt,
        PartySummary createdBy
) {
}
