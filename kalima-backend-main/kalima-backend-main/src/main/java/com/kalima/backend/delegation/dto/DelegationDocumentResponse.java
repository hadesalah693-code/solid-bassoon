package com.kalima.backend.delegation.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record DelegationDocumentResponse(
        UUID id,
        UUID delegationId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String description,
        DomainEnums.DelegationDocumentType documentType,
        PartySummary uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
