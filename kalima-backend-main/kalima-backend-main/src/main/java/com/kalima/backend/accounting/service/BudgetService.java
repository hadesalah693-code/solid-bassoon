package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.BudgetCreateRequest;
import com.kalima.backend.accounting.dto.BudgetResponse;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.Budget;
import com.kalima.backend.accounting.repository.BudgetRepository;
import com.kalima.backend.accounting.repository.JournalLineRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Budgets.
 *
 * <p>A budget is a target only: it never posts an entry and never stores its own
 * actual. The actual is computed from {@code journal_lines} over the budget's date
 * range every time it is read, so it cannot drift away from the accounts the way a
 * stored figure would the moment a bill or payment lands.
 */
@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final JournalLineRepository journalLineRepository;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public BudgetService(BudgetRepository budgetRepository,
                         JournalLineRepository journalLineRepository,
                         AccountingService accounting,
                         AuditLogService auditLogService) {
        this.budgetRepository = budgetRepository;
        this.journalLineRepository = journalLineRepository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(Integer fiscalYear) {
        UUID tenantId = TenantContext.getRequired();
        List<Budget> budgets = fiscalYear == null
                ? budgetRepository.findAllByCompany(tenantId)
                : budgetRepository.findByCompanyIdAndYear(tenantId, fiscalYear);
        return budgets.stream().map(this::withActual).toList();
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(UUID id) {
        return withActual(load(id));
    }

    @Transactional
    public BudgetResponse create(@Valid BudgetCreateRequest request) {
        ResolvedPeriod resolved = resolvePeriod(request);

        Account account = expenseAccount(request);

        Budget budget = Budget.of(accounting.currentTenantReference());
        budget.setName(request.name().trim());
        budget.setFiscalYear(resolved.start().getYear());
        budget.setPeriod(resolved.period());
        budget.setPeriodStart(resolved.start());
        budget.setPeriodEnd(resolved.end());
        budget.setAccount(account);
        budget.setBudgetAmount(request.budgetAmount());
        budget.setNotes(request.notes());
        budget.setCreatedBy(accounting.currentUser());

        Budget saved;
        try {
            saved = budgetRepository.save(budget);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "يوجد ميزانية بنفس الاسم لنفس الفترة");
        }

        auditLogService.recordSuccess("ACCOUNTING_BUDGET_CREATED", accounting.currentUser(),
                "Budget", saved.getId(),
                java.util.Map.of("name", saved.getName(),
                        "amount", saved.getBudgetAmount().toPlainString()));
        return withActual(saved);
    }

    @Transactional
    public BudgetResponse update(UUID id, @Valid BudgetCreateRequest request) {
        Budget budget = load(id);
        ResolvedPeriod resolved = resolvePeriod(request);

        budget.setName(request.name().trim());
        budget.setFiscalYear(resolved.start().getYear());
        budget.setPeriod(resolved.period());
        budget.setPeriodStart(resolved.start());
        budget.setPeriodEnd(resolved.end());
        budget.setBudgetAmount(request.budgetAmount());
        budget.setNotes(request.notes());
        budget.setAccount(expenseAccount(request));

        Budget saved = budgetRepository.save(budget);
        auditLogService.recordSuccess("ACCOUNTING_BUDGET_UPDATED", accounting.currentUser(),
                "Budget", saved.getId(),
                java.util.Map.of("name", saved.getName(),
                        "amount", saved.getBudgetAmount().toPlainString()));
        return withActual(saved);
    }

    private record ResolvedPeriod(Budget.Period period, LocalDate start, LocalDate end) {
    }

    /**
     * Works out the dates a budget covers.
     *
     * <p>A monthly or quarterly budget has to name which month or quarter it is for.
     * Defaulting those to the whole financial year would quietly report one twelfth
     * or one quarter of spending against a year's target, which reads as a serious
     * underspend, so the request has to say which slice it means.
     */
    private ResolvedPeriod resolvePeriod(@Valid BudgetCreateRequest request) {
        Budget.Period period = request.period() == null ? Budget.Period.CUSTOM : request.period();

        if (period != Budget.Period.CUSTOM
                && request.periodStart() != null
                && request.periodEnd() != null) {
            return new ResolvedPeriod(period, request.periodStart(), request.periodEnd());
        }

        switch (period) {
            case CUSTOM -> {
                if (request.periodStart() == null || request.periodEnd() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "الميزانية المخصصة تحتاج تاريخ بداية ونهاية");
                }
                return new ResolvedPeriod(period, request.periodStart(), request.periodEnd());
            }
            case MONTH -> {
                if (request.periodMonth() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "الميزانية الشهرية تحتاج رقم الشهر");
                }
                int year = fiscalYear(request.fiscalYear());
                int month = request.periodMonth();
                if (month < 1 || month > 12) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "رقم الشهر يجب أن يكون بين 1 و 12");
                }
                return new ResolvedPeriod(period,
                        LocalDate.of(year, month, 1),
                        LocalDate.of(year, month, 1)
                                .withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth()));
            }
            case QUARTER -> {
                if (request.quarter() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "الميزانية الربع سنوية تحتاج رقم الربع");
                }
                int year = fiscalYear(request.fiscalYear());
                int quarter = request.quarter();
                if (quarter < 1 || quarter > 4) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "رقم الربع يجب أن يكون بين 1 و 4");
                }
                int firstMonth = (quarter - 1) * 3 + 1;
                LocalDate first = LocalDate.of(year, firstMonth, 1);
                LocalDate lastMonth = LocalDate.of(year, firstMonth + 2, 1);
                return new ResolvedPeriod(period, first, lastMonth.withDayOfMonth(
                        lastMonth.lengthOfMonth()));
            }
            case YEAR -> {
                int year = fiscalYear(request.fiscalYear());
                return new ResolvedPeriod(period,
                        LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31));
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "فترة الميزانية غير مدعومة");
        }
    }

    private int fiscalYear(Integer requested) {
        int year = requested == null ? LocalDate.now().getYear() : requested;
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "السنة المالية يجب أن تكون بين 2000 و 2100");
        }
        return year;
    }

    private Account expenseAccount(BudgetCreateRequest request) {
        if (request.accountId() == null) {
            return null;
        }
        Account account = accounting.requireAccountById(request.accountId());
        if (account.getType() != Account.Type.EXPENSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ميزانية المصروفات يجب أن ترتبط بحساب مصروفات");
        }
        return account;
    }

    @Transactional
    public void delete(UUID id) {
        Budget budget = load(id);
        budgetRepository.delete(budget);
        auditLogService.recordSuccess("ACCOUNTING_BUDGET_DELETED", accounting.currentUser(),
                "Budget", budget.getId(), java.util.Map.of());
    }

    private BudgetResponse withActual(Budget budget) {
        BigDecimal actual = journalLineRepository.budgetActual(
                TenantContext.getRequired(),
                budget.getAccount() == null ? null : budget.getAccount().getId(),
                budget.getPeriodStart(),
                budget.getPeriodEnd());
        return BudgetResponse.from(budget, actual);
    }

    private Budget load(UUID id) {
        return budgetRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الميزانية غير موجودة"));
    }
}