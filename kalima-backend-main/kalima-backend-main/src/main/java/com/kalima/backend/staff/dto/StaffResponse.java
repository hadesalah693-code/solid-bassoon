package com.kalima.backend.staff.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record StaffResponse(
        UUID id,
        UUID companyId,
        DomainEnums.SystemRole systemRole,
        String fullName,
        String email,
        String nationalId,
        String phone,
        String address,
        String notes,
        boolean isActive,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
