package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.casefile.dto.PartySummary;

import java.time.LocalDateTime;
import java.util.UUID;

public record LaborComplaintDocumentResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String description,
        PartySummary uploadedBy,
        LocalDateTime createdAt
) {
}
