package com.kalima.backend.calendar.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CalendarEventPatchRequest(
        String title,
        String description,
        DomainEnums.CalendarEventType eventType,
        UUID caseId,
        UUID clientId,
        UUID disputeId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        DomainEnums.CalendarEventStatus status,
        DomainEnums.TaskPriority priority,
        UUID assignedToId,
        List<Integer> reminderOffsetsMinutes
) {
}
