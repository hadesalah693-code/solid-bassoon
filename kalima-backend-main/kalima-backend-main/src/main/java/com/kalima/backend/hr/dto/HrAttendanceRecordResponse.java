package com.kalima.backend.hr.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record HrAttendanceRecordResponse(
        UUID id,
        UUID deviceId,
        String deviceName,
        UUID employeeId,
        String employeeName,
        String employeeNumber,
        String deviceUserId,
        LocalDateTime punchedAt,
        DomainEnums.HrPunchType punchType,
        DomainEnums.HrVerifyMode verifyMode,
        String workCode
) {
    /** True when the punch ID on the device has no employee linked to it yet. */
    public boolean unmatched() {
        return employeeId == null;
    }
}
