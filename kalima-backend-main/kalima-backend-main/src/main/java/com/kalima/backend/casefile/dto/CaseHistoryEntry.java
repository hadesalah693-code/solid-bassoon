package com.kalima.backend.casefile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One audit entry projected for the case-history timeline.
 *
 * <p>This is intentionally a narrow slice of {@code AuditLogResponse}:
 * only the fields a timeline UI actually needs. Sensitive infra details
 * ({@code ipAddress}, {@code userAgent}) are omitted because non-ADMIN
 * roles (e.g. CLIENT, ACCOUNTANT) can see a case's history.
 *
 * <p>{@code metadata} is the original audit JSON string — the frontend
 * may render selected keys (old/new status, hearing date, task status…)
 * for richer line items.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaseHistoryEntry(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        UUID actorPersonId,
        String actorName,
        String actorEmail,
        String metadata,
        boolean success,
        LocalDateTime createdAt
) {
}
