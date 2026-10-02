package com.kalima.backend.casefile.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fields allowed to change after creation. Deliberately excludes
 * {@code caseNumber} and {@code clientId}: the case number is a tenant-wide
 * identifier that paper/court records rely on, and re-assigning a case to a
 * different client is a compliance-sensitive move that should go through its
 * own dedicated endpoint later (not free-form PUT).
 *
 * <p>Status changes have their own endpoint
 * ({@link CaseStatusUpdateRequest}).
 */
public record CaseUpdateRequest(
        @Size(max = 100, message = "courtCaseNumber must be at most 100 characters")
        String courtCaseNumber,

        @NotBlank(message = "title is required")
        @Size(max = 250, message = "title must be at most 250 characters")
        String title,

        String description,

        @Size(max = 100, message = "caseType must be at most 100 characters")
        String caseType,

        @Size(max = 200, message = "courtName must be at most 200 characters")
        String courtName,

        @Size(max = 100, message = "courtCircuit must be at most 100 characters")
        String courtCircuit,

        @Size(max = 100, message = "procedureStage must be at most 100 characters")
        String procedureStage,

        LocalDate startDate,

        LocalDate closeDate,

        UUID assignedLawyerId,

        DomainEnums.CasePriority priority
) {
}
