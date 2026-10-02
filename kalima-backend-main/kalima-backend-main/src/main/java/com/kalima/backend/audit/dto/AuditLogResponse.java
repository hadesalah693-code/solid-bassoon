package com.kalima.backend.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogResponse(
        UUID id,
        UUID companyId,
        UUID actorPersonId,
        String actorEmail,
        String action,
        String entityType,
        UUID entityId,
        String ipAddress,
        String userAgent,
        String metadata,
        boolean success,
        String errorMessage,
        LocalDateTime createdAt
) {
}
