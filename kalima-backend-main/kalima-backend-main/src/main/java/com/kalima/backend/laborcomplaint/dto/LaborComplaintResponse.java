package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LaborComplaintResponse(
        UUID id,
        String complaintNumber,
        int complaintYear,
        LocalDate registrationDate,
        LocalDate receiptDate,
        String subject,
        String summary,
        String legalRequest,
        DomainEnums.LaborComplaintStatus status,
        PartySummary client,
        List<LaborComplaintClientResponse> clients,
        PartySummary assignedLawyer,
        String opponentName,
        DomainEnums.LaborComplaintPartyCapacity opponentCapacity,
        List<LaborComplaintOpponentResponse> opponents,
        String savedReason,
        LocalDateTime savedAt,
        String referralNotes,
        LocalDateTime referredAt,
        UUID linkedCaseId,
        String linkedCaseNumber,
        UUID linkedDisputeId,
        String linkedDisputeTitle,
        String internalReference,
        String externalPartners,
        String opponentRepresentative,
        String cooperatingOffices,
        LaborComplaintDisputeTeamSnapshot linkedDisputeTeam,
        List<LaborComplaintMolSessionResponse> molSessions,
        PartySummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
