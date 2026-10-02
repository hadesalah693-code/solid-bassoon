package com.kalima.backend.calendar.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CalendarEventCreateRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull DomainEnums.CalendarEventType eventType,
        UUID caseId,
        UUID clientId,
        UUID disputeId,
        @NotNull LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        DomainEnums.TaskPriority priority,
        UUID assignedToId,
        List<Integer> reminderOffsetsMinutes
) {
}
