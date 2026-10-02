package com.kalima.backend.caseparticipant.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record ParticipantResponse(
        UUID id,
        UUID caseId,
        PartySummary person,
        DomainEnums.CaseParticipantRole role,
        String notes,
        LocalDateTime createdAt
) {
}
