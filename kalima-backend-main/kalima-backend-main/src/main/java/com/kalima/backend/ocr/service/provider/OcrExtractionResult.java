package com.kalima.backend.ocr.service.provider;

import com.kalima.backend.domain.entity.DomainEnums;

import java.math.BigDecimal;

public record OcrExtractionResult(
        String text,
        BigDecimal confidenceScore,
        String language,
        DomainEnums.OcrProvider provider
) {
}
