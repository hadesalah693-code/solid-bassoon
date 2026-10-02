package com.kalima.backend.template.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        DomainEnums.TemplateType type,
        String content,
        boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
