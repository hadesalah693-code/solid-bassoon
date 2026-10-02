package com.kalima.backend.template.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Detailed view of a {@code GeneratedDocument}, including the rendered
 * content and lightweight references back to the source template / linked
 * client / linked case so the UI can show context without extra round-trips.
 *
 * <p>Reference fields (e.g. {@code clientName}, {@code caseNumber}) are
 * nullable on purpose: the source document may have been generated without a
 * client or without a case.
 */
public record GeneratedDocumentResponse(
        UUID id,
        UUID templateId,
        String templateName,
        DomainEnums.TemplateType templateType,
        UUID clientId,
        String clientName,
        UUID caseId,
        String caseNumber,
        String title,
        DomainEnums.DocumentStatus status,
        String renderedContent,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
