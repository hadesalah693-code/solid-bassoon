package com.kalima.backend.ocr.service;

import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.ocr.config.OcrAsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OcrProcessingExecutor {

    private static final Logger log = LoggerFactory.getLogger(OcrProcessingExecutor.class);

    private final OcrProcessingService processingService;

    public OcrProcessingExecutor(OcrProcessingService processingService) {
        this.processingService = processingService;
    }

    @Async(OcrAsyncConfig.OCR_EXECUTOR)
    public void executeAsync(UUID recordId, UUID companyId, UUID actorId) {
        TenantContext.set(companyId);
        try {
            processingService.executeProcessing(recordId, actorId);
        } catch (Exception ex) {
            log.warn("Unexpected OCR async failure for {}: {}", recordId, ex.getMessage(), ex);
        } finally {
            TenantContext.clear();
        }
    }
}
