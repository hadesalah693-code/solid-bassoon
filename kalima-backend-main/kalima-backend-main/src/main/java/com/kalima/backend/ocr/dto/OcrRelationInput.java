package com.kalima.backend.ocr.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OcrRelationInput(
        @NotNull(message = "relationType is required")
        DomainEnums.OcrRelationType relationType,

        @NotNull(message = "relationId is required")
        UUID relationId
) {
}
