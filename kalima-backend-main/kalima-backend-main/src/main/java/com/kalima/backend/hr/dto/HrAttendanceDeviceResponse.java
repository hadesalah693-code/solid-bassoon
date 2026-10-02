package com.kalima.backend.hr.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record HrAttendanceDeviceResponse(
        UUID id,
        String name,
        String location,
        String ipAddress,
        int port,
        DomainEnums.HrDeviceProtocol protocol,
        String serialNumber,
        DomainEnums.HrDeviceStatus status,
        LocalDateTime lastSyncAt,
        String lastSyncMessage
) {
}
