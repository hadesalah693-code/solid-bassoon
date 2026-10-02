package com.kalima.backend.disputedocument.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record DisputeDocumentResponse(
        UUID id,
        UUID disputeId,
        UUID stageId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        DomainEnums.CaseDocumentCategory category,
        DomainEnums.CaseDocumentVisibility visibility,
        String description,
        PartySummary uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
