package com.kalima.backend.mandate.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

/** Response for a mandate document row. */
public record MandateDocumentResponse(
        UUID id,
        UUID mandateId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String description,
        DomainEnums.MandateDocumentType documentType,
        PartySummary uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
