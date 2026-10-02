package com.kalima.backend.auth.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        PersonInfo person
) {
    public record PersonInfo(
            UUID id,
            String fullName,
            String email,
            DomainEnums.SystemRole systemRole
    ) {
    }
}
