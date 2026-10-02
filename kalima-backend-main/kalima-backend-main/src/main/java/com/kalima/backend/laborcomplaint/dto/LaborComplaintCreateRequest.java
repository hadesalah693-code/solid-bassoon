package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LaborComplaintCreateRequest(
        LocalDate registrationDate,
        LocalDate receiptDate,
        Integer complaintYear,
        @Size(max = 32, message = "complaintNumber must be at most 32 characters")
        String complaintNumber,
        @Size(max = 10000, message = "subject must be at most 10000 characters")
        String subject,
        @Size(max = 10000, message = "summary must be at most 10000 characters")
        String summary,
        @Size(max = 10000, message = "legalRequest must be at most 10000 characters")
        String legalRequest,
        UUID clientId,
        @Valid List<LaborComplaintClientInput> clients,
        UUID assignedLawyerId,
        @Size(max = 250, message = "opponentName must be at most 250 characters")
        String opponentName,
        DomainEnums.LaborComplaintPartyCapacity opponentCapacity,
        @Valid List<LaborComplaintOpponentInput> opponents,
        UUID linkedDisputeId,
        @Size(max = 250, message = "internalReference must be at most 250 characters")
        String internalReference,
        @Size(max = 5000, message = "externalPartners must be at most 5000 characters")
        String externalPartners,
        @Size(max = 250, message = "opponentRepresentative must be at most 250 characters")
        String opponentRepresentative,
        @Size(max = 5000, message = "cooperatingOffices must be at most 5000 characters")
        String cooperatingOffices,
        @Valid List<LaborComplaintMolSessionInput> molSessions
) {
}
