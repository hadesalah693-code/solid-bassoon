package com.kalima.backend.ocr.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDate;
import java.util.UUID;

public record OcrSearchParams(
        String q,
        DomainEnums.OcrStatus status,
        DomainEnums.OcrDocumentType documentType,
        UUID uploadedBy,
        LocalDate from,
        LocalDate to,
        UUID caseId,
        UUID clientId,
        UUID disputeId,
        UUID taskId,
        UUID documentId
) {
}
