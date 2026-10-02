package com.kalima.backend.dispute.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DisputeCaseLinkResponse(
        UUID id,
        UUID disputeId,
        UUID caseId,
        String linkRole,
        LocalDateTime createdAt
) {
}
