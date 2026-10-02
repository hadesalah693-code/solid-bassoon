package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.ExpenseCreateRequest;
import com.kalima.backend.accounting.dto.ExpenseResponse;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.Expense;
import com.kalima.backend.accounting.entity.Expense.Category;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.repository.ExpenseRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final AuditLogService auditLogService;
    private final AccountingService accounting;

    public ExpenseService(ExpenseRepository expenseRepository, AccountingService accounting,
                          AuditLogService auditLogService) {
        this.expenseRepository = expenseRepository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    private static String accountForCategory(Category c) {
        return switch (c) {
            case SALARIES -> AccountingService.ACC_SALARIES;
            case RENT -> AccountingService.ACC_RENT;
            case UTILITIES -> AccountingService.ACC_UTILITIES;
            case MARKETING -> AccountingService.ACC_MARKETING;
            case SOFTWARE -> AccountingService.ACC_SOFTWARE;
            case GOVERNMENT_FEES -> AccountingService.ACC_GOV_FEES;
            case LEGAL_FEES -> AccountingService.ACC_LEGAL_FEES;
            case TRANSPORT -> AccountingService.ACC_TRANSPORT;
            case SUPPLIES -> AccountingService.ACC_SUPPLIES;
            case OTHER -> AccountingService.ACC_OTHER_EXPENSE;
        };
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> list(Category category, LocalDate from, LocalDate to,
                                      String search, Pageable pageable) {
        String term = StringUtils.hasText(search) ? search.trim() : null;
        return expenseRepository.search(TenantContext.getRequired(), category,
                        AccountingDates.from(from), AccountingDates.to(to), term, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID id) {
        return expenseRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "المصروف غير موجود"));
    }

    @Transactional
    public ExpenseResponse create(ExpenseCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();

        BigDecimal subtotal = request.subtotal().setScale(2, RoundingMode.HALF_UP);
        if (subtotal.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "المبلغ لا يمكن أن يكون سالباً");
        }
        BigDecimal taxRate = request.taxRate() == null ? BigDecimal.ZERO : request.taxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "نسبة الضريبة يجب أن تكون بين 0 و 100");
        }
        if (subtotal.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "المصروف يجب أن يكون أكبر من صفر");
        }
        BigDecimal taxAmount = subtotal.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(taxAmount);

        Account expenseAccount = request.accountId() != null
                ? accounting.requireAccountById(request.accountId())
                : accounting.requireAccount(accountForCategory(request.category()));
        if (expenseAccount.getType() != Account.Type.EXPENSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "يجب اختيار حساب مصروفات لتسجيل المصروف");
        }

        BankAccount bank = null;
        if (request.bankAccountId() != null) {
            bank = accounting.findBankAccount(request.bankAccountId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحساب البنكي غير موجود"));
        } else {
            bank = accounting.defaultCashAccount().orElse(null);
        }
        Person client = request.clientId() != null ? accounting.requirePerson(request.clientId()) : null;

        Expense expense = new Expense();
        expense.setCompany(accounting.currentTenantReference());
        expense.setExpenseNumber(accounting.nextNumber(
                com.kalima.backend.accounting.entity.AccountingSequence.Scope.EXPENSE,
                request.expenseDate()));
        expense.setExpenseDate(request.expenseDate());
        expense.setCategory(request.category());
        expense.setDescription(request.description().trim());
        expense.setAccount(expenseAccount);
        expense.setBankAccount(bank);
        expense.setClient(client);
        expense.setSubtotal(subtotal);
        expense.setTaxRate(taxRate);
        expense.setTaxAmount(taxAmount);
        expense.setTotal(total);
        expense.setRecurring(Boolean.TRUE.equals(request.recurring()));
        if (StringUtils.hasText(request.payee())) expense.setPayee(request.payee().trim());
        if (StringUtils.hasText(request.paymentMethod())) expense.setPaymentMethod(request.paymentMethod().trim());
        if (StringUtils.hasText(request.reference())) expense.setReference(request.reference().trim());
        if (StringUtils.hasText(request.notes())) expense.setNotes(request.notes().trim());
        expense.setRecordedBy(accounting.currentUser());

        // The document is saved first so the journal entry can carry its id. Posting
        // with a null referenceId left the entry untraceable and silently disabled the
        // duplicate-posting guard, which starts with `if (referenceId == null) return false`.
        Expense saved = expenseRepository.saveAndFlush(expense);

        // Double entry: Dr expense (+ VAT input) / Cr cash
        // Only the cash leg carries the bankAccount tag; tagging the debit legs
        // too would double-count the expense in per-bank movement reports.
        Account creditSide = bank != null ? bank.getAccount() : accounting.requireAccount(AccountingService.ACC_CASH);
        List<AccountingService.Line> lines = new java.util.ArrayList<>();
        lines.add(AccountingService.Line.dr(subtotal, expenseAccount, expense.getDescription(), client, null));
        if (taxAmount.signum() > 0) {
            lines.add(AccountingService.Line.dr(taxAmount, accounting.requireAccount(AccountingService.ACC_VAT_INPUT),
                    "ضريبة مشتريات", null, null));
        }
        lines.add(AccountingService.Line.cr(total, creditSide, "سداد " + expense.getDescription(), null, bank));

        JournalEntry entry = accounting.post(request.expenseDate(),
                expense.getExpenseNumber() + " — " + expense.getDescription(),
                JournalEntry.Source.EXPENSE, "EXPENSE", saved.getId(), accounting.currentUser(), lines);
        saved.setJournalEntry(entry);
        return toResponse(expenseRepository.save(saved));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        Expense expense = expenseRepository.findByCompany_IdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "المصروف غير موجود"));
        if (expense.getJournalEntry() != null) {
            // Reversing an already-reversed entry would throw deep in the engine; the
            // ledger is already corrected, so only the document needs clearing.
            if (expense.getJournalEntry().getReversedEntry() == null) {
                accounting.reverse(expense.getJournalEntry(), accounting.currentUser());
            }
        }
        expense.setVoidedAt(Instant.now());
        expenseRepository.save(expense);
        auditLogService.recordSuccess("ACCOUNTING_EXPENSE_VOIDED", accounting.currentUser(),
            "Expense", expense.getId(), java.util.Map.of(
                "expenseNumber", expense.getExpenseNumber(),
                "journalEntryId", expense.getJournalEntry() == null ? "" : expense.getJournalEntry().getId().toString()));
    }

    private ExpenseResponse toResponse(Expense e) {
        return new ExpenseResponse(
                e.getId(), e.getExpenseNumber(), e.getExpenseDate(), e.getCategory(),
                e.getPayee(), e.getDescription(),
                e.getAccount() == null ? null : e.getAccount().getId(),
                e.getAccount() == null ? null : e.getAccount().getCode(),
                e.getAccount() == null ? null : e.getAccount().getNameAr(),
                e.getBankAccount() == null ? null : e.getBankAccount().getId(),
                e.getBankAccount() == null ? null : e.getBankAccount().getNameAr(),
                e.getClient() == null ? null : e.getClient().getId(),
                e.getClient() == null ? null : e.getClient().getFullName(),
                e.getSubtotal(), e.getTaxRate(), e.getTaxAmount(), e.getTotal(),
                e.getPaymentMethod(), e.getReference(), e.isRecurring(),
                e.getJournalEntry() == null ? null : e.getJournalEntry().getId(),
                e.getJournalEntry() == null ? null : e.getJournalEntry().getEntryNumber(),
                e.getNotes(),
                e.getRecordedBy() == null ? null : e.getRecordedBy().getFullName(),
                e.getCreatedAt() == null ? null : LocalDate.ofInstant(e.getCreatedAt(),
                        java.time.ZoneId.systemDefault()));
    }
}
