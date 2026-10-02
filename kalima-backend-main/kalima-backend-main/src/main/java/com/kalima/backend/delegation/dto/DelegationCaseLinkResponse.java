package com.kalima.backend.delegation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DelegationCaseLinkResponse(
        UUID id,
        UUID delegationId,
        UUID caseId,
        String caseNumber,
        String caseTitle,
        LocalDateTime createdAt
) {
}
