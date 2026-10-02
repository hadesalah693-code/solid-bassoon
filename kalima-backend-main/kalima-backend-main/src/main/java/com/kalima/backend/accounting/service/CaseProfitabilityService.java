package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.repository.JournalLineRepository;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Case profitability.
 *
 * <p>Every figure here is aggregated from {@code journal_lines} where
 * {@code case_file_id} is set, never from invoice and expense documents. Reading the
 * ledger is what makes this trustworthy: an invoice that was voided, a credit note
 * that was issued or a payment that was reversed has already moved the numbers
 * through its journal entry, so the report and the accounts cannot disagree.
 */
@Service
public class CaseProfitabilityService {

    private final JournalLineRepository journalLineRepository;
    private final CaseFileRepository caseFileRepository;

    public CaseProfitabilityService(JournalLineRepository journalLineRepository,
                                    CaseFileRepository caseFileRepository) {
        this.journalLineRepository = journalLineRepository;
        this.caseFileRepository = caseFileRepository;
    }

    @Transactional(readOnly = true)
    public List<CaseProfitability> report(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfYear(1) : from;

        Map<UUID, BigDecimal[]> byCase = new HashMap<>();
        for (Object[] row : journalLineRepository.caseProfitability(tenantId, start, end)) {
            UUID caseId = (UUID) row[0];
            BigDecimal revenue = row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1];
            BigDecimal cost = row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2];
            byCase.put(caseId, new BigDecimal[]{revenue, cost});
        }

        List<CaseProfitability> result = new ArrayList<>(byCase.size());
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Map.Entry<UUID, BigDecimal[]> entry : byCase.entrySet()) {
            BigDecimal revenue = entry.getValue()[0];
            BigDecimal cost = entry.getValue()[1];
            totalRevenue = totalRevenue.add(revenue);
            totalCost = totalCost.add(cost);

            CaseFile file = caseFileRepository.findScopedById(entry.getKey()).orElse(null);
            result.add(new CaseProfitability(
                    entry.getKey(),
                    file == null ? null : file.getCaseNumber(),
                    file == null ? null : file.getTitle(),
                    revenue,
                    cost,
                    revenue.subtract(cost),
                    marginPercent(revenue, revenue.subtract(cost))));
        }
        result.sort((a, b) -> b.profit().compareTo(a.profit()));
        return result;
    }

    @Transactional(readOnly = true)
    public CaseProfitability forCase(UUID caseId, LocalDate from, LocalDate to) {
        CaseFile file = caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "القضية غير موجودة"));
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfYear(1) : from;
        BigDecimal revenue = nz(journalLineRepository.caseRevenue(
                TenantContext.getRequired(), caseId, start, end));
        BigDecimal cost = nz(journalLineRepository.caseCost(
                TenantContext.getRequired(), caseId, start, end));
        return new CaseProfitability(caseId, file.getCaseNumber(), file.getTitle(),
                revenue, cost, revenue.subtract(cost),
                marginPercent(revenue, revenue.subtract(cost)));
    }

    @Transactional(readOnly = true)
    public PortfolioSummary summary(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfYear(1) : from;

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        int profitable = 0;
        int losing = 0;
        int breakeven = 0;

        for (Object[] row : journalLineRepository.caseProfitability(tenantId, start, end)) {
            BigDecimal r = row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1];
            BigDecimal c = row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2];
            revenue = revenue.add(r);
            cost = cost.add(c);
            BigDecimal profit = r.subtract(c);
            if (profit.signum() > 0) {
                profitable++;
            } else if (profit.signum() < 0) {
                losing++;
            } else {
                breakeven++;
            }
        }
        return new PortfolioSummary(revenue, cost, revenue.subtract(cost),
                marginPercent(revenue, revenue.subtract(cost)),
                profitable, losing, breakeven);
    }

    public record CaseProfitability(UUID caseId, String caseNumber, String caseTitle,
                                    BigDecimal revenue, BigDecimal cost,
                                    BigDecimal profit, BigDecimal marginPercent) {
    }

    public record PortfolioSummary(BigDecimal revenue, BigDecimal cost,
                                   BigDecimal profit, BigDecimal marginPercent,
                                   int profitableCases, int losingCases,
                                   int breakevenCases) {
    }

    private BigDecimal marginPercent(BigDecimal revenue, BigDecimal profit) {
        if (revenue.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return profit.multiply(new BigDecimal("100"))
                .divide(revenue, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}