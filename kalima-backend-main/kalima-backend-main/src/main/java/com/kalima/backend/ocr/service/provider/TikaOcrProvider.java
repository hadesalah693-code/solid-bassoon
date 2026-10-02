package com.kalima.backend.ocr.service.provider;

import com.kalima.backend.domain.entity.DomainEnums;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fallback OCR via Apache Tika — extracts embedded text layers only (not true OCR
 * on scanned images). Used when Azure is unavailable or as a dev fallback.
 */
@Component
public class TikaOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(TikaOcrProvider.class);
    private static final int MAX_TEXT_CHARS = 500_000;

    private final Tika tika = new Tika();

    @Override
    public String providerId() {
        return DomainEnums.OcrProvider.TIKA.name();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public OcrExtractionResult extract(Path file, String contentType, String originalFilename)
            throws OcrProviderException {
        if (!Files.isRegularFile(file)) {
            throw new OcrProviderException("OCR source file not found.");
        }
        try (InputStream in = Files.newInputStream(file)) {
            String text = tika.parseToString(in).trim();
            if (text.isEmpty()) {
                throw new OcrProviderException(
                        "No extractable text found. Scanned documents require Azure Document Intelligence.");
            }
            if (text.length() > MAX_TEXT_CHARS) {
                text = text.substring(0, MAX_TEXT_CHARS) + "…";
            }
            return new OcrExtractionResult(
                    text,
                    null,
                    OcrLanguageDetector.fromText(text),
                    DomainEnums.OcrProvider.TIKA);
        } catch (OcrProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Tika extraction failed for {}: {}", originalFilename, ex.getMessage());
            throw new OcrProviderException("Text extraction failed.", ex);
        }
    }
}
