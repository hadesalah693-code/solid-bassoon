package com.kalima.backend.template.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a new fillable form / document template.
 *
 * <p>{@code content} is the raw template body. It may include the supported
 * placeholders documented on the document-generation service
 * (e.g. {@code {{today}}}, {@code {{client.name}}}, {@code {{case.number}}},
 * {@code {{company.name}}}); they are substituted only at render-time, so the
 * stored template keeps the original placeholders intact for re-use.
 */
public record CreateTemplateRequest(
        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @NotNull(message = "type is required")
        DomainEnums.TemplateType type,

        @NotBlank(message = "content is required")
        String content,

        Boolean isDefault
) {
}
