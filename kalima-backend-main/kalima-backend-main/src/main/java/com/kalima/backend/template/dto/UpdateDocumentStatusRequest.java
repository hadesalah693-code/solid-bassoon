package com.kalima.backend.template.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;

public record UpdateDocumentStatusRequest(
        @NotNull(message = "status is required")
        DomainEnums.DocumentStatus status
) {
}
