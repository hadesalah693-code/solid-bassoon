package com.kalima.backend.ocr.service.provider;

import java.nio.file.Path;

/**
 * Pluggable OCR engine. Business logic uses {@link OcrProviderRegistry} only.
 */
public interface OcrProvider {

    /** Stable id matching {@link com.kalima.backend.domain.entity.DomainEnums.OcrProvider}. */
    String providerId();

    boolean isAvailable();

    OcrExtractionResult extract(Path file, String contentType, String originalFilename)
            throws OcrProviderException;
}
