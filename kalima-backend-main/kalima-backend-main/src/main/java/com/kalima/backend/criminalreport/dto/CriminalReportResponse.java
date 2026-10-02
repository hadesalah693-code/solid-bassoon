package com.kalima.backend.criminalreport.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CriminalReportResponse(
        UUID id,
        String reportNumber,
        int reportYear,
        LocalDate registrationDate,
        LocalDate incidentDate,
        String prosecutionOffice,
        String securityCenter,
        String charge,
        String subject,
        String summary,
        String legalRequest,
        DomainEnums.CriminalReportStatus status,
        PartySummary client,
        List<CriminalReportClientResponse> clients,
        PartySummary assignedLawyer,
        String opponentName,
        DomainEnums.CriminalReportPartyCapacity opponentCapacity,
        List<CriminalReportOpponentResponse> opponents,
        String archiveReason,
        Integer grievancePeriodDays,
        LocalDate grievanceDeadline,
        LocalDateTime archivedAt,
        String finalArchiveReason,
        String decisionIssuer,
        String decisionNumber,
        LocalDate decisionReceivedDate,
        String finalDecisionBasis,
        Integer appealPeriodDays,
        String finalDecisionLevel,
        LocalDateTime finalArchivedAt,
        UUID linkedCaseId,
        String linkedCaseNumber,
        UUID linkedDisputeId,
        String linkedDisputeTitle,
        String internalSource,
        String legalConsultant,
        String externalConsultant,
        String partnerNotes,
        List<CriminalReportLocationResponse> locations,
        List<CriminalReportInvestigationSessionResponse> investigationSessions,
        PartySummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
