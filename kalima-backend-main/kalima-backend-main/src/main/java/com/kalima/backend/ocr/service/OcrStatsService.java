package com.kalima.backend.ocr.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.ocr.config.OcrProperties;
import com.kalima.backend.ocr.dto.OcrStatsResponse;
import com.kalima.backend.ocr.repository.OcrRecordStatsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OcrStatsService {

    private static final int SUCCESS_RATE_WINDOW_DAYS = 30;
    private static final int TOP_USERS_LIMIT = 5;

    private final OcrRecordStatsRepository statsRepository;
    private final CurrentUserService currentUserService;
    private final OcrProperties properties;

    public OcrStatsService(OcrRecordStatsRepository statsRepository,
                             CurrentUserService currentUserService,
                             OcrProperties properties) {
        this.statsRepository = statsRepository;
        this.currentUserService = currentUserService;
        this.properties = properties;
    }

    public OcrStatsResponse stats() {
        ensureEnabled();
        rejectClient(currentUserService.requireCurrentPerson());

        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime windowStart = today.minusDays(SUCCESS_RATE_WINDOW_DAYS).atStartOfDay();

        long total = statsRepository.countAllScoped();
        long processedToday = statsRepository.countCompletedBetween(
                DomainEnums.OcrStatus.COMPLETED, dayStart, dayEnd);
        long processing = statsRepository.countByStatus(DomainEnums.OcrStatus.PROCESSING);
        long failed = statsRepository.countByStatus(DomainEnums.OcrStatus.FAILED);

        long terminal = statsRepository.countTerminalSince(
                List.of(DomainEnums.OcrStatus.COMPLETED, DomainEnums.OcrStatus.FAILED),
                windowStart);
        long success = statsRepository.countSuccessSince(
                DomainEnums.OcrStatus.COMPLETED, windowStart);
        double successRate = terminal == 0 ? 0.0 : (success * 100.0) / terminal;

        return new OcrStatsResponse(
                total,
                processedToday,
                processing,
                failed,
                Math.round(successRate * 10.0) / 10.0,
                statsRepository.findMostActiveUploaders(TenantContext.getRequired(), TOP_USERS_LIMIT));
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OCR module is disabled.");
        }
    }

    private static void rejectClient(Person actor) {
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "OCR is not available to clients.");
        }
    }
}
