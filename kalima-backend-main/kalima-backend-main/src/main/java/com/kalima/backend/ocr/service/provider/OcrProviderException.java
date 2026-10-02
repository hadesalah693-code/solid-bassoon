package com.kalima.backend.ocr.service.provider;

/**
 * Recoverable OCR engine failure — mapped to {@code FAILED} status on the record.
 */
public class OcrProviderException extends Exception {

    public OcrProviderException(String message) {
        super(message);
    }

    public OcrProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
