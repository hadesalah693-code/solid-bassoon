package com.kalima.backend.template.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for editing an existing template. Behaves like a PUT (full replace
 * of editable fields), matching the convention used by {@code CaseUpdateRequest}.
 */
public record UpdateTemplateRequest(
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
