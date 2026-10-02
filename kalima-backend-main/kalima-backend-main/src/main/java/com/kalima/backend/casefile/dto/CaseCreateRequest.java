package com.kalima.backend.casefile.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CaseCreateRequest(
        @Size(max = 100, message = "caseNumber must be at most 100 characters")
        String caseNumber,

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

        DomainEnums.CaseStatus status,

        LocalDate startDate,

        @NotNull(message = "clientId is required")
        UUID clientId,

        UUID assignedLawyerId,

        DomainEnums.DisputePathType disputePathType,

        DomainEnums.CasePriority priority,

        UUID parentCaseId
) {
}
