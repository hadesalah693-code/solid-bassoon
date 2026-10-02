package com.kalima.backend.calendar.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarAgendaDayResponse(
        LocalDate date,
        List<CalendarEventResponse> events
) {
}
