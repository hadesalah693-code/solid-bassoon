package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LaborComplaintListItemResponse(
        UUID id,
        String complaintNumber,
        int complaintYear,
        LocalDate registrationDate,
        LocalDate receiptDate,
        String subject,
        DomainEnums.LaborComplaintStatus status,
        String clientName,
        UUID clientId,
        String clientNames,
        String assignedLawyerName,
        String opponentName,
        String opponentNames,
        String internalReference,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
