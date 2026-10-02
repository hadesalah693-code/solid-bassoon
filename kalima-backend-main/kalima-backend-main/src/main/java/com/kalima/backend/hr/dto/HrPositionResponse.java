package com.kalima.backend.hr.dto;

import java.util.UUID;

public record HrPositionResponse(
        UUID id,
        String title,
        String code,
        UUID departmentId,
        String departmentName,
        String description,
        boolean active
) {
}
