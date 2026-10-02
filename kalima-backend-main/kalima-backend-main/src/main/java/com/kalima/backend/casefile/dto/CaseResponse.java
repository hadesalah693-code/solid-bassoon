package com.kalima.backend.casefile.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaseResponse(
        UUID id,
        String caseNumber,
        String courtCaseNumber,
        String title,
        String description,
        DomainEnums.CaseStatus status,
        DomainEnums.CasePriority priority,
        UUID parentCaseId,
        String caseType,
        String courtName,
        String courtCircuit,
        String procedureStage,
        DomainEnums.DisputePathType disputePathType,
        LocalDate startDate,
        LocalDate closeDate,
        LocalDateTime archivedAt,
        PartySummary client,
        PartySummary assignedLawyer,
        PartySummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
