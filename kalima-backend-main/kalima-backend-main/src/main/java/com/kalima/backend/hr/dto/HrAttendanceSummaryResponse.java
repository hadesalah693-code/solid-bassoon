package com.kalima.backend.hr.dto;

import java.time.LocalDate;
import java.util.List;

/** Headline counters shown above the daily attendance sheet. */
public record HrAttendanceSummaryResponse(
        LocalDate date,
        int totalEmployees,
        int present,
        int absent,
        int late,
        int earlyLeave,
        int unmatchedPunches,
        List<HrAttendanceDayRow> rows
) {
}
