package com.kalima.backend.criminalreport.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CriminalReportStatusUpdateRequest(
        @NotNull(message = "status is required")
        DomainEnums.CriminalReportStatus status,
        String archiveReason,
        Integer grievancePeriodDays,
        LocalDate grievanceDeadline,
        String finalArchiveReason,
        String decisionIssuer,
        String decisionNumber,
        LocalDate decisionReceivedDate,
        String finalDecisionBasis,
        Integer appealPeriodDays,
        String finalDecisionLevel,
        UUID linkedCaseId
) {
}
