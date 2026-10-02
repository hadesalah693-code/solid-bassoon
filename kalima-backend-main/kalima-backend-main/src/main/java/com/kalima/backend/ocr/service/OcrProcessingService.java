package com.kalima.backend.ocr.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.ocr.entity.OcrRecord;
import com.kalima.backend.ocr.repository.OcrRecordRepository;
import com.kalima.backend.ocr.service.provider.OcrExtractionResult;
import com.kalima.backend.ocr.service.provider.OcrProviderException;
import com.kalima.backend.ocr.service.provider.OcrProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class OcrProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OcrProcessingService.class);

    private final OcrRecordRepository recordRepository;
    private final PersonRepository personRepository;
    private final OcrStorageService storageService;
    private final OcrProviderRegistry providerRegistry;
    private final AuditLogService auditLogService;
    private final OcrProcessingExecutor processingExecutor;

    public OcrProcessingService(OcrRecordRepository recordRepository,
                                PersonRepository personRepository,
                                OcrStorageService storageService,
                                OcrProviderRegistry providerRegistry,
                                AuditLogService auditLogService,
                                @Lazy OcrProcessingExecutor processingExecutor) {
        this.recordRepository = recordRepository;
        this.personRepository = personRepository;
        this.storageService = storageService;
        this.providerRegistry = providerRegistry;
        this.auditLogService = auditLogService;
        this.processingExecutor = processingExecutor;
    }

    /**
     * Marks a record as queued for OCR and kicks off async execution.
     */
    @Transactional
    public void queueProcessing(UUID recordId, Person actor, boolean rerun) {
        OcrRecord record = loadRecord(recordId);
        assertProcessable(record, rerun);

        if (rerun) {
            record.setExtractedText(null);
            record.setEditedText(null);
            record.setConfidenceScore(null);
            record.setLanguage(null);
            record.setProvider(null);
            auditLogService.recordSuccess(
                    AuditAction.OCR_RERUN,
                    actor,
                    "OcrRecord",
                    record.getId(),
                    Map.of("filename", record.getOriginalFilename()));
        } else {
            auditLogService.recordSuccess(
                    AuditAction.OCR_PROCESS_STARTED,
                    actor,
                    "OcrRecord",
                    record.getId(),
                    Map.of("filename", record.getOriginalFilename()));
        }

        record.setStatus(DomainEnums.OcrStatus.PROCESSING);
        record.setProcessingStartedAt(LocalDateTime.now());
        record.setProcessingCompletedAt(null);
        record.setErrorMessage(null);
        recordRepository.save(record);

        processingExecutor.executeAsync(
                record.getId(),
                record.getCompany().getId(),
                actor.getId());
    }

    @Transactional
    public void executeProcessing(UUID recordId, UUID actorId) {
        OcrRecord record = loadRecord(recordId);
        if (record.getStatus() != DomainEnums.OcrStatus.PROCESSING) {
            log.debug("Skipping OCR execute for {} — status is {}", recordId, record.getStatus());
            return;
        }

        Person actor = personRepository.findScopedById(actorId).orElse(null);
        Path file = storageService.filePath(record);

        try {
            OcrExtractionResult result = providerRegistry.extract(
                    file,
                    record.getContentType(),
                    record.getOriginalFilename());

            record.setExtractedText(result.text());
            record.setConfidenceScore(result.confidenceScore());
            record.setLanguage(result.language());
            record.setProvider(result.provider());
            record.setStatus(DomainEnums.OcrStatus.COMPLETED);
            record.setProcessingCompletedAt(LocalDateTime.now());
            record.setErrorMessage(null);
            recordRepository.save(record);

            if (actor != null) {
                auditLogService.recordSuccess(
                        AuditAction.OCR_COMPLETED,
                        actor,
                        "OcrRecord",
                        record.getId(),
                        Map.of(
                                "provider", result.provider().name(),
                                "confidence", result.confidenceScore() == null
                                        ? ""
                                        : result.confidenceScore().toPlainString(),
                                "language", result.language() == null ? "" : result.language()));
            }
        } catch (OcrProviderException ex) {
            markFailed(record, actor, ex.getMessage());
        } catch (Exception ex) {
            log.warn("OCR processing failed for {}: {}", recordId, ex.getMessage(), ex);
            markFailed(record, actor, "Unexpected OCR processing error.");
        }
    }

    private void markFailed(OcrRecord record, Person actor, String message) {
        String safeMessage = message == null || message.isBlank()
                ? "OCR processing failed."
                : abbreviate(message, 2_000);
        record.setStatus(DomainEnums.OcrStatus.FAILED);
        record.setErrorMessage(safeMessage);
        record.setProcessingCompletedAt(LocalDateTime.now());
        recordRepository.save(record);

        if (actor != null) {
            auditLogService.recordFailure(
                    AuditAction.OCR_FAILED,
                    actor,
                    null,
                    "OcrRecord",
                    record.getId(),
                    Map.of(
                            "filename", record.getOriginalFilename(),
                            "error", safeMessage),
                    safeMessage);
        }
    }

    private static void assertProcessable(OcrRecord record, boolean rerun) {
        DomainEnums.OcrStatus status = record.getStatus();
        if (status == DomainEnums.OcrStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "OCR processing is already in progress.");
        }
        if (rerun) {
            if (status != DomainEnums.OcrStatus.PENDING
                    && status != DomainEnums.OcrStatus.FAILED
                    && status != DomainEnums.OcrStatus.COMPLETED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "OCR record cannot be re-processed in status: " + status);
            }
            return;
        }
        if (status != DomainEnums.OcrStatus.PENDING && status != DomainEnums.OcrStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "OCR record is not pending processing.");
        }
    }

    private OcrRecord loadRecord(UUID id) {
        return recordRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "OCR record not found: " + id));
    }

    private static String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
