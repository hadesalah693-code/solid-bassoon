package com.kalima.backend.hr.dto;

import java.time.LocalDateTime;

/** Result of the "test connection" button on the devices screen. */
public record HrDeviceTestResponse(
        boolean reachable,
        String ipAddress,
        int port,
        String serialNumber,
        String firmware,
        LocalDateTime deviceTime,
        String message
) {
}
