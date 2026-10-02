package com.kalima.backend.casefile.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CaseInternalNoteResponse(
        UUID id,
        String content,
        PartySummary author,
        LocalDateTime createdAt
) {
}