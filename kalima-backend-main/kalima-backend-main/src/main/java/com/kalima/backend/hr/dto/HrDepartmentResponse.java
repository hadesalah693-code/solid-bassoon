package com.kalima.backend.hr.dto;

import com.kalima.backend.casefile.dto.PartySummary;

import java.util.UUID;

public record HrDepartmentResponse(
        UUID id,
        String name,
        String code,
        UUID parentId,
        String parentName,
        PartySummary manager,
        String description,
        boolean active
) {
}
