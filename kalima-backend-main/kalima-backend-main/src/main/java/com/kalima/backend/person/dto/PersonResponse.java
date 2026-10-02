package com.kalima.backend.person.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record PersonResponse(
        UUID id,
        DomainEnums.PersonType personType,
        DomainEnums.SystemRole systemRole,
        String fullName,
        String companyName,
        String nationalId,
        String commercialRegister,
        String phone,
        String email,
        String address,
        String notes,
        boolean active,
        LocalDateTime lastLoginAt,
        /** Non-null if the person has been soft-archived (replaces hard delete in API). */
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
