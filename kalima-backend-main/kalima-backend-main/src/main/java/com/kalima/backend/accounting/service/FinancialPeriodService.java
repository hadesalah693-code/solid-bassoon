package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.FinancialPeriodRequest;
import com.kalima.backend.accounting.dto.FinancialPeriodResponse;
import com.kalima.backend.accounting.entity.FinancialPeriod;
import com.kalima.backend.accounting.entity.FinancialPeriod.Status;
import com.kalima.backend.accounting.repository.FinancialPeriodRepository;
import com.kalima.backend.accounting.repository.JournalEntryRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialPeriodService {

    private final FinancialPeriodRepository periodRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingService accounting;

    public FinancialPeriodService(FinancialPeriodRepository periodRepository,
                                  JournalEntryRepository journalEntryRepository,
                                  AccountingService accounting) {
        this.periodRepository = periodRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.accounting = accounting;
    }

    @Transactional(readOnly = true)
    public List<FinancialPeriodResponse> list() {
        UUID tenantId = TenantContext.getRequired();
        return periodRepository.findByCompany_IdOrderByStartDateDesc(tenantId).stream()
                .map(p -> toResponse(p, journalEntryRepository.countByCompany_IdAndPostedTrueAndEntryDateBetween(
                        tenantId, p.getStartDate(), p.getEndDate())))
                .toList();
    }

    @Transactional
    public FinancialPeriodResponse create(FinancialPeriodRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "تاريخ النهاية يجب أن يكون بعد تاريخ البداية");
        }
        UUID tenantId = TenantContext.getRequired();
        if (periodRepository.findByCompany_IdAndName(tenantId, request.name().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "اسم الفترة مستخدم مسبقاً");
        }
        for (FinancialPeriod existing : periodRepository.findByCompany_IdOrderByStartDateDesc(tenantId)) {
            if (overlaps(existing, request.startDate(), request.endDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "الفترة تتداخل مع الفترة " + existing.getName());
            }
        }

        FinancialPeriod period = new FinancialPeriod();
        period.setCompany(accounting.currentTenantReference());
        period.setName(request.name().trim());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setStatus(Status.OPEN);
        return toResponse(periodRepository.save(period), 0L);
    }

    @Transactional
    public FinancialPeriodResponse close(UUID id) {
        FinancialPeriod period = require(id);
        if (period.getStatus() != Status.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الفترة مغلقة بالفعل");
        }
        period.setStatus(Status.CLOSED);
        period.setClosedAt(Instant.now());
        period.setClosedBy(accounting.currentUser());
        return toResponse(periodRepository.save(period), entryCount(period));
    }

    @Transactional
    public FinancialPeriodResponse reopen(UUID id) {
        FinancialPeriod period = require(id);
        if (period.getStatus() == Status.LOCKED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "الفترة مقفلة ولا يمكن إعادة فتحها");
        }
        period.setStatus(Status.OPEN);
        period.setClosedAt(null);
        period.setClosedBy(null);
        return toResponse(periodRepository.save(period), entryCount(period));
    }

    private boolean overlaps(FinancialPeriod existing, LocalDate from, LocalDate to) {
        return !from.isAfter(existing.getEndDate()) && !to.isBefore(existing.getStartDate());
    }

    private FinancialPeriod require(UUID id) {
        return periodRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الفترة غير موجودة"));
    }

    private long entryCount(FinancialPeriod p) {
        return journalEntryRepository.countByCompany_IdAndPostedTrueAndEntryDateBetween(
                TenantContext.getRequired(), p.getStartDate(), p.getEndDate());
    }

    private FinancialPeriodResponse toResponse(FinancialPeriod p, long entryCount) {
        return new FinancialPeriodResponse(
                p.getId(), p.getName(), p.getStartDate(), p.getEndDate(), p.getStatus(),
                p.getClosedAt(), p.getClosedBy() == null ? null : p.getClosedBy().getFullName(), entryCount);
    }
}
