package com.kalima.backend.ocr.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** List/summary view — never includes extracted or edited text bodies. */
public record OcrRecordListResponse(
        UUID id,
        String title,
        String originalFilename,
        String contentType,
        long sizeBytes,
        DomainEnums.OcrDocumentType documentType,
        String language,
        BigDecimal confidenceScore,
        DomainEnums.OcrStatus status,
        String errorMessage,
        DomainEnums.OcrProvider provider,
        PartySummary uploadedBy,
        LocalDateTime processingStartedAt,
        LocalDateTime processingCompletedAt,
        List<OcrRelationResponse> relations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
