package com.kalima.backend.calendar.dto;

public record CalendarSummaryResponse(
        long todayCount,
        long upcomingWeekCount,
        long overdueCount,
        long hearingsThisWeekCount,
        long tasksDueThisWeekCount,
        long meetingsThisWeekCount
) {
}
