package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.AccountingReportResponse;
import com.kalima.backend.accounting.dto.AccountingReportResponse.AccountAmount;
import com.kalima.backend.accounting.dto.AccountingReportResponse.AgingBucket;
import com.kalima.backend.accounting.dto.AccountLedgerResponse;
import com.kalima.backend.accounting.dto.AccountingReportResponse.BankBalance;
import com.kalima.backend.accounting.dto.AccountingReportResponse.ClientStatementResponse;
import com.kalima.backend.accounting.dto.AccountingReportResponse.StatementRow;
import com.kalima.backend.accounting.dto.AccountingReportResponse.TrialRow;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.ClientAccount;
import com.kalima.backend.accounting.entity.Expense;
import com.kalima.backend.accounting.repository.AccountRepository;
import com.kalima.backend.accounting.repository.BankAccountRepository;
import com.kalima.backend.accounting.repository.ClientAccountRepository;
import com.kalima.backend.accounting.repository.ExpenseRepository;
import com.kalima.backend.accounting.repository.JournalLineRepository;
import com.kalima.backend.accounting.repository.ReceiptRepository;
import com.kalima.backend.accounting.repository.TaxRateRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import com.kalima.backend.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountingReportService {

    private static final List<InvoiceStatus> OPEN_INVOICE_STATUSES = List.of(
            InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);
    private static final List<InvoiceStatus> AGING_STATUSES = List.of(
            InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE);

    private final AccountRepository accountRepository;
    private final BankAccountRepository bankAccountRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final ExpenseRepository expenseRepository;
    private final ReceiptRepository receiptRepository;
    private final TaxRateRepository taxRateRepository;
    private final InvoiceRepository invoiceRepository;
    private final JournalLineRepository journalLineRepository;
    private final AccountingService accounting;

    public AccountingReportService(AccountRepository accountRepository,
                                   BankAccountRepository bankAccountRepository,
                                   ClientAccountRepository clientAccountRepository,
                                   ExpenseRepository expenseRepository,
                                   ReceiptRepository receiptRepository,
                                   TaxRateRepository taxRateRepository,
                                   InvoiceRepository invoiceRepository,
                                   JournalLineRepository journalLineRepository,
                                   AccountingService accounting) {
        this.accountRepository = accountRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.clientAccountRepository = clientAccountRepository;
        this.expenseRepository = expenseRepository;
        this.receiptRepository = receiptRepository;
        this.taxRateRepository = taxRateRepository;
        this.invoiceRepository = invoiceRepository;
        this.journalLineRepository = journalLineRepository;
        this.accounting = accounting;
    }

    // ------------------------------------------------------------------
    // Combined dashboard report
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public AccountingReportResponse report(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate start = from != null ? from : LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate end = to != null ? to : LocalDate.now();
        if (start.isAfter(end)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "تاريخ البداية يجب ألا يتجاوز تاريخ النهاية");
        }

        List<Account> accounts = accountRepository.findByCompany_IdOrderByCodeAsc(tenantId);
        Map<UUID, BigDecimal[]> movement = movementByAccount(tenantId, start, end);
        Map<UUID, BigDecimal[]> lifetime = movementByAccount(tenantId, null, end);
        // ---- Profit & loss (revenue - expenses), revenue is credit-positive
        List<AccountAmount> revenueBreakdown = new ArrayList<>();
        List<AccountAmount> expenseBreakdown = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        for (Account a : accounts) {
            BigDecimal[] m = movement.get(a.getId());
            if (m == null) continue;
            BigDecimal dr = m[0], cr = m[1];
            BigDecimal net;
            if (a.getType() == Account.Type.REVENUE) {
                net = cr.subtract(dr);
                if (net.signum() != 0) {
                    revenueBreakdown.add(amount(a, dr, cr, net));
                    revenue = revenue.add(net);
                }
            } else if (a.getType() == Account.Type.EXPENSE) {
                net = dr.subtract(cr);
                if (net.signum() != 0) {
                    expenseBreakdown.add(amount(a, dr, cr, net));
                    expenses = expenses.add(net);
                }
            }
        }
        revenueBreakdown.sort(Comparator.comparing(AccountAmount::code));
        expenseBreakdown.sort(Comparator.comparing(AccountAmount::code));
        BigDecimal netProfit = revenue.subtract(expenses);

        // ---- Balance sheet (cumulative balances)
        List<AccountAmount> assets = new ArrayList<>();
        List<AccountAmount> liabilities = new ArrayList<>();
        List<AccountAmount> equity = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        // The sheet is a point-in-time statement, so the result that closes it must be
        // cumulative since inception up to `end` — not the selected period's profit.
        // Folding one month's profit into lifetime equity made the sheet
        // unbalanceable: the moment anything was ever posted to 3100 Capital, or the
        // report was filtered to a single month, Assets stopped equalling L + E.
        BigDecimal lifetimeRevenue = BigDecimal.ZERO;
        BigDecimal lifetimeExpenses = BigDecimal.ZERO;
        for (Account a : accounts) {
            BigDecimal[] m = lifetime.get(a.getId());
            if (m == null) continue;
            if (a.getType() == Account.Type.REVENUE) {
                lifetimeRevenue = lifetimeRevenue.add(m[1].subtract(m[0]));
            } else if (a.getType() == Account.Type.EXPENSE) {
                lifetimeExpenses = lifetimeExpenses.add(m[0].subtract(m[1]));
            }
        }
        BigDecimal netProfitToDate = lifetimeRevenue.subtract(lifetimeExpenses);

        BigDecimal totalEquity = BigDecimal.ZERO;
        for (Account a : accounts) {
            BigDecimal[] m = lifetime.get(a.getId());
            BigDecimal dr = m == null ? BigDecimal.ZERO : m[0];
            BigDecimal cr = m == null ? BigDecimal.ZERO : m[1];
            BigDecimal net = switch (a.getType()) {
                case ASSET -> dr.subtract(cr);
                case LIABILITY -> cr.subtract(dr);
                case EQUITY -> cr.subtract(dr);
                default -> BigDecimal.ZERO;
            };
            if (net.signum() == 0) continue;
            switch (a.getType()) {
                case ASSET -> {
                    assets.add(amount(a, dr, cr, net));
                    totalAssets = totalAssets.add(net);
                }
                case LIABILITY -> {
                    liabilities.add(amount(a, dr, cr, net));
                    totalLiabilities = totalLiabilities.add(net);
                }
                case EQUITY -> {
                    equity.add(amount(a, dr, cr, net));
                    totalEquity = totalEquity.add(net);
                }
                default -> { }
            }
        }
        // Undistributed profit since inception is not yet a posted account, so it is
        // presented inside equity rather than left to silently break the identity.
        if (netProfitToDate.signum() != 0) {
            totalEquity = totalEquity.add(netProfitToDate);
        }
        BigDecimal totalLiabEquity = totalLiabilities.add(totalEquity);

        // ---- Trial balance
        List<TrialRow> trial = new ArrayList<>();
        BigDecimal trialDebit = BigDecimal.ZERO;
        BigDecimal trialCredit = BigDecimal.ZERO;
        for (Account a : accounts) {
            BigDecimal[] m = lifetime.get(a.getId());
            if (m == null || (m[0].signum() == 0 && m[1].signum() == 0)) continue;
            trial.add(new TrialRow(a.getId(), a.getCode(), a.getNameAr(), a.getType(),
                    m[0], m[1], m[0].subtract(m[1])));
            trialDebit = trialDebit.add(m[0]);
            trialCredit = trialCredit.add(m[1]);
        }

        // ---- Cash
        // Balance comes from the ledger account, so this figure is identical to the
        // one the trial balance and the bank screen show. The previous
        // `openingBalance + movement-by-bank-tag` formula produced a third number that
        // matched neither, because the opening column was never posted.
        List<BankBalance> bankBalances = new ArrayList<>();
        BigDecimal cashOnHand = BigDecimal.ZERO;
        BigDecimal cashIn = BigDecimal.ZERO;
        BigDecimal cashOut = BigDecimal.ZERO;
        for (BankAccount b : bankAccountRepository.findByCompany_IdOrderByNameArAsc(tenantId)) {
                BigDecimal balance = orZero(bankAccountRepository.sumLedgerBalanceAsOf(
                    tenantId, b.getAccount().getId(), end));
                BigDecimal opening = orZero(bankAccountRepository.sumLedgerBalanceBySourceAsOf(
                    tenantId, b.getAccount().getId(), com.kalima.backend.accounting.entity.JournalEntry.Source.OPENING, end));
            BigDecimal periodMove = orZero(bankAccountRepository.sumMovement(tenantId, b.getId(),
                    AccountingDates.from(start), AccountingDates.to(end)));
            cashOnHand = cashOnHand.add(balance);
            cashIn = cashIn.add(periodMove.max(BigDecimal.ZERO));
            cashOut = cashOut.add(periodMove.negate().max(BigDecimal.ZERO));
            bankBalances.add(new BankBalance(b.getId(), b.getNameAr(), b.getBankName(), b.getCurrency(),
                    opening, balance.subtract(opening), balance));
        }

        // ---- Receivables / AR aging
        BigDecimal totalReceivable = BigDecimal.ZERO;
        long overdueCount = 0L;
        for (Object[] row : invoiceRepository.arAging(tenantId, AGING_STATUSES, end)) {
            totalReceivable = totalReceivable.add(orZero(toDecimal(row[5])));
            overdueCount += toLong(row[6]);
        }

        Map<String, BigDecimal> bucketAmount = new HashMap<>();
        Map<String, Long> bucketCount = new HashMap<>();
        for (Object[] row : invoiceRepository.arAgingBuckets(tenantId, AGING_STATUSES, end)) {
            String key = (String) row[0];
            bucketAmount.put(key, orZero(toDecimal(row[2])));
            bucketCount.put(key, toLong(row[1]));
        }
        List<AgingBucket> aging = List.of(
                bucket("غير مستحق", "CURRENT", bucketAmount, bucketCount),
                bucket("1 - 30 يوم", "D1_30", bucketAmount, bucketCount),
                bucket("31 - 60 يوم", "D31_60", bucketAmount, bucketCount),
                bucket("61 - 90 يوم", "D61_90", bucketAmount, bucketCount),
                bucket("أكثر من 90 يوم", "D90_PLUS", bucketAmount, bucketCount),
                bucket("بدون تاريخ استحقاق", "UNDATED", bucketAmount, bucketCount));

        // ---- Tax
        // Read from the VAT control accounts so this figure cannot disagree with the
        // VAT line in the balance sheet. It used to be summed from invoices.tax_amount
        // and expenses.tax_amount, which silently diverged from the ledger whenever a
        // manual journal touched 2200/2210 or an invoice was returned to draft.
        BigDecimal outputVat = periodNet(accounts, movement, AccountingService.ACC_VAT_PAYABLE, true);
        BigDecimal inputVat = periodNet(accounts, movement, AccountingService.ACC_VAT_INPUT, false);

        return new AccountingReportResponse(
                start, end,
                revenue, BigDecimal.ZERO, revenue, expenses, netProfit,
                revenueBreakdown, expenseBreakdown,
                totalAssets, totalLiabilities, totalEquity, totalLiabEquity,
                totalAssets.compareTo(totalLiabEquity) == 0,
                assets, liabilities, equity,
                trial, trialDebit, trialCredit, trialDebit.compareTo(trialCredit) == 0,
                cashOnHand, cashIn, cashOut, bankBalances,
                totalReceivable, overdueCount, aging,
                outputVat, inputVat, outputVat.subtract(inputVat));
    }

    // ------------------------------------------------------------------
    // Client statement
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ClientStatementResponse clientStatement(UUID clientId, LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusMonths(3);

        // A client account row is provisioned lazily on first billing, so fall back
        // to the default receivable account rather than failing the statement.
        String clientName = accounting.requirePerson(clientId).getFullName();
        UUID arAccountId = clientAccountRepository.findByCompany_IdAndPerson_Id(tenantId, clientId)
                .map(ClientAccount::getAccount)
                .map(Account::getId)
                .orElseGet(() -> accounting.requireAccount(AccountingService.ACC_AR).getId());

        BigDecimal opening = clientAccountRepository.findByCompany_IdAndPerson_Id(tenantId, clientId)
                .map(ClientAccount::getOpeningBalance)
                .orElse(BigDecimal.ZERO)
                .add(orZero(journalLineRepository.clientBalanceBefore(tenantId, clientId, arAccountId, start)));

        List<StatementRow> rows = new ArrayList<>();
        BigDecimal running = opening;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (Object[] row : journalLineRepository.clientStatementRows(tenantId, clientId, arAccountId, start, end)) {
            BigDecimal debit = orZero(toDecimal(row[4]));
            BigDecimal credit = orZero(toDecimal(row[5]));
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            running = running.add(debit).subtract(credit);
            rows.add(new StatementRow(
                    (LocalDate) row[0], (String) row[1], (String) row[3], debit, credit, running));
        }

        return new ClientStatementResponse(
                clientId, clientName, start, end,
                opening, totalDebit, totalCredit, running, rows);
    }

        @Transactional(readOnly = true)
        public AccountLedgerResponse accountLedger(
            UUID accountId, LocalDate from, LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? LocalDate.of(end.getYear(), 1, 1) : from;
        if (start.isAfter(end)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "تاريخ البداية يجب ألا يتجاوز تاريخ النهاية");
        }
        Account account = accountRepository.findByCompany_IdAndId(tenantId, accountId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "الحساب غير موجود"));
        BigDecimal opening = orZero(journalLineRepository.accountBalanceBefore(tenantId, accountId, start));
        Object[] sums = journalLineRepository.accountPeriodTotals(tenantId, accountId, start, end);
        BigDecimal debit = orZero(toDecimal(sums == null ? null : sums[0]));
        BigDecimal credit = orZero(toDecimal(sums == null ? null : sums[1]));
        var rows = journalLineRepository.accountLedgerRows(tenantId, accountId, start, end, pageable)
            .map(row -> new AccountLedgerResponse.Row(
                (LocalDate) row[0], (String) row[1], (String) row[2], (UUID) row[3],
                (String) row[4], orZero(toDecimal(row[5])), orZero(toDecimal(row[6]))));
        return new AccountLedgerResponse(account.getId(), account.getCode(), account.getNameAr(),
            account.getType(), start, end, opening, debit, credit,
            opening.add(debit).subtract(credit), rows);
        }

        // ------------------------------------------------------------------
        // helpers
    // ------------------------------------------------------------------
    private Map<UUID, BigDecimal[]> movementByAccount(UUID tenantId, LocalDate from, LocalDate to) {
        Map<UUID, BigDecimal[]> map = new HashMap<>();
        List<UUID> ids = accountRepository.findByCompany_IdOrderByCodeAsc(tenantId)
                .stream().map(Account::getId).toList();
        if (ids.isEmpty()) return map;
        for (Object[] row : accountRepository.debitCreditByAccount(tenantId, ids,
                AccountingDates.from(from), AccountingDates.to(to))) {
            map.put((UUID) row[0], new BigDecimal[]{orZero(toDecimal(row[1])), orZero(toDecimal(row[2]))});
        }
        return map;
    }

    private static AccountAmount amount(Account a, BigDecimal dr, BigDecimal cr, BigDecimal balance) {
        return new AccountAmount(a.getId(), a.getCode(), a.getNameAr(), a.getType(), dr, cr, balance);
    }

    /**
     * Signed period movement of one system account. {@code creditPositive} selects a
     * credit-balance account such as VAT Payable, otherwise a debit-balance account
     * such as VAT Input.
     */
    private BigDecimal periodNet(List<Account> accounts, Map<UUID, BigDecimal[]> movement,
                                 String code, boolean creditPositive) {
        for (Account a : accounts) {
            if (code.equals(a.getCode())) {
                BigDecimal[] m = movement.get(a.getId());
                if (m == null) return BigDecimal.ZERO;
                return creditPositive ? m[1].subtract(m[0]) : m[0].subtract(m[1]);
            }
        }
        return BigDecimal.ZERO;
    }

    private static AgingBucket bucket(String label, String key,
                                      Map<String, BigDecimal> amounts, Map<String, Long> counts) {
        return new AgingBucket(label, amounts.getOrDefault(key, BigDecimal.ZERO),
                counts.getOrDefault(key, 0L));
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }
}
