package com.kalima.backend.template.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for rendering a {@code Template} into a new {@code GeneratedDocument}.
 *
 * <p>{@code clientId} and {@code caseId} are both optional. If null, the
 * placeholders that depend on them ({@code {{client.name}}},
 * {@code {{case.number}}}) render as the empty string instead of crashing.
 *
 * <p>{@code title} is optional: when blank, the service falls back to the
 * template name as the document's title.
 */
public record GenerateDocumentRequest(
        @NotNull(message = "templateId is required")
        UUID templateId,

        UUID clientId,

        UUID caseId,

        @Size(max = 250, message = "title must be at most 250 characters")
        String title
) {
}
