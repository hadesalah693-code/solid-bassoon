package com.kalima.backend.ocr.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record OcrRelationResponse(
        UUID id,
        DomainEnums.OcrRelationType relationType,
        UUID relationId,
        LocalDateTime createdAt
) {
}
