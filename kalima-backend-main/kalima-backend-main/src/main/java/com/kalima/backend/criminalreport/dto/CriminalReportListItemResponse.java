package com.kalima.backend.criminalreport.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CriminalReportListItemResponse(
        UUID id,
        String reportNumber,
        int reportYear,
        LocalDate registrationDate,
        String prosecutionOffice,
        String securityCenter,
        String charge,
        DomainEnums.CriminalReportStatus status,
        String clientName,
        UUID clientId,
        String clientNames,
        String assignedLawyerName,
        String opponentName,
        String opponentNames,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
