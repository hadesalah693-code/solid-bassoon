package com.kalima.backend.criminalreport.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CriminalReportUpdateRequest(
        LocalDate registrationDate,
        @Size(max = 32, message = "reportNumber must be at most 32 characters")
        String reportNumber,
        LocalDate incidentDate,
        @Size(max = 200, message = "prosecutionOffice must be at most 200 characters")
        String prosecutionOffice,
        @Size(max = 200, message = "securityCenter must be at most 200 characters")
        String securityCenter,
        @Size(max = 500, message = "charge must be at most 500 characters")
        String charge,
        @Size(max = 10000, message = "subject must be at most 10000 characters")
        String subject,
        @Size(max = 10000, message = "summary must be at most 10000 characters")
        String summary,
        @Size(max = 10000, message = "legalRequest must be at most 10000 characters")
        String legalRequest,
        UUID assignedLawyerId,
        @Size(max = 250, message = "opponentName must be at most 250 characters")
        String opponentName,
        DomainEnums.CriminalReportPartyCapacity opponentCapacity,
        @Valid List<CriminalReportClientInput> clients,
        @Valid List<CriminalReportOpponentInput> opponents,
        UUID linkedDisputeId,
        @Size(max = 250, message = "internalSource must be at most 250 characters")
        String internalSource,
        @Size(max = 250, message = "legalConsultant must be at most 250 characters")
        String legalConsultant,
        @Size(max = 250, message = "externalConsultant must be at most 250 characters")
        String externalConsultant,
        @Size(max = 5000, message = "partnerNotes must be at most 5000 characters")
        String partnerNotes,
        @Valid List<CriminalReportLocationInput> locations,
        @Valid List<CriminalReportInvestigationSessionInput> investigationSessions
) {
}
