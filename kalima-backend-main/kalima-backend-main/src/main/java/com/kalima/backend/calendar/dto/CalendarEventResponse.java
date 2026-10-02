package com.kalima.backend.calendar.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CalendarEventResponse(
        UUID id,
        String title,
        String description,
        DomainEnums.CalendarEventType eventType,
        DomainEnums.CalendarSourceModule sourceModule,
        UUID sourceId,
        UUID caseId,
        String caseNumber,
        String caseTitle,
        UUID clientId,
        String clientName,
        UUID taskId,
        UUID disputeId,
        String disputeNumber,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean allDay,
        DomainEnums.CalendarEventStatus status,
        DomainEnums.TaskPriority priority,
        UUID assignedToId,
        String assignedToName,
        UUID createdById,
        String createdByName,
        String colorKey,
        boolean editable,
        String sourceHref,
        List<Integer> reminderOffsetsMinutes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
