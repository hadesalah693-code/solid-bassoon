package com.kalima.backend.hr.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One employee's day on the attendance sheet, derived from their raw punches.
 *
 * @param firstPunch    earliest punch of the day
 * @param lastPunch     latest punch of the day
 * @param workedMinutes worked time between them, or 0 when the day is incomplete
 */
public record HrAttendanceDayRow(
        LocalDate date,
        java.util.UUID employeeId,
        String employeeNumber,
        String employeeName,
        String departmentName,
        String positionTitle,
        DomainEnums.HrEmployeeStatus employeeStatus,
        LocalDateTime firstPunch,
        LocalDateTime lastPunch,
        long workedMinutes,
        DomainEnums.HrAttendanceDayStatus status,
        List<HrAttendanceRecordResponse> punches
) {
}
