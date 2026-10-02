package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.repository.BankReconciliationRepository;
import com.kalima.backend.accounting.repository.JournalLineRepository;
import com.kalima.backend.accounting.repository.VendorBillRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import com.kalima.backend.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Management dashboard.
 *
 * <p>Every figure is derived from the ledger or from document status; nothing here
 * is stored. If cash, receivables or revenue is wrong on this screen, it is wrong in
 * the accounts too, which is what makes the screen safe to run a firm from.
 */
@Service
public class AccountingDashboardService {

    private static final List<InvoiceStatus> OPEN_INVOICE_STATUSES =
            List.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);

    private final AccountingService accountingService;
    private final JournalLineRepository journalLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final VendorBillRepository vendorBillRepository;
    private final BankReconciliationRepository reconciliationRepository;
    private final CaseProfitabilityService profitabilityService;
    private final PayablesService payablesService;

    public AccountingDashboardService(AccountingService accounting,
                                      JournalLineRepository journalLineRepository,
                                      InvoiceRepository invoiceRepository,
                                      VendorBillRepository vendorBillRepository,
                                      BankReconciliationRepository reconciliationRepository,
                                      CaseProfitabilityService profitabilityService,
                                      PayablesService payablesService) {
        this.accountingService = accounting;
        this.journalLineRepository = journalLineRepository;
        this.invoiceRepository = invoiceRepository;
        this.vendorBillRepository = vendorBillRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.profitabilityService = profitabilityService;
        this.payablesService = payablesService;
    }

    @Transactional(readOnly = true)
    public DashboardSnapshot snapshot(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            start = end;
        }

        BigDecimal cash = balance(AccountingService.ACC_CASH)
                .add(balance(AccountingService.ACC_BANK))
                .add(balance(AccountingService.ACC_BANK_UNRECONCILED));
        BigDecimal receivables = balance(AccountingService.ACC_AR)
                .add(balance(AccountingService.ACC_UNBILLED_AR));
        BigDecimal payables = balance(AccountingService.ACC_AP);
        BigDecimal retainerLiability = balance(AccountingService.ACC_RETAINER_LIABILITY)
                .add(balance(AccountingService.ACC_CLIENT_CREDIT));

        // Revenue and expenses are read as revenue-type and expense-type movements
        // rather than a hand-listed set of accounts, so a new account added to the
        // chart of accounts shows up on the dashboard without touching this code.
        BigDecimal revenue = journalLineRepository.movementByType(
                tenantId, Account.Type.REVENUE, start, end);
        BigDecimal expenses = journalLineRepository.movementByType(
                tenantId, Account.Type.EXPENSE, start, end);

        BigDecimal arOverdue = overdueReceivables(tenantId, end);
        long overdueBills = vendorBillRepository.countOverdue(tenantId, end);

        return new DashboardSnapshot(
                start,
                end,
                cash,
                receivables,
                payables,
                retainerLiability,
                revenue,
                expenses,
                revenue.subtract(expenses),
                arOverdue,
                nz(payablesService.summary(end).outstandingPayable()),
                overdueBills,
                countReconciliationsInProgress(),
                profitabilityService.summary(start, end));
    }

    public record DashboardSnapshot(LocalDate from, LocalDate to,
                                    BigDecimal cash, BigDecimal receivables,
                                    BigDecimal payables, BigDecimal clientLiabilities,
                                    BigDecimal revenue, BigDecimal expenses,
                                    BigDecimal netProfit,
                                    BigDecimal arOverdue,
                                    BigDecimal outstandingPayable,
                                    long overdueBills,
                                    int reconciliationsInProgress,
                                    CaseProfitabilityService.PortfolioSummary profitability) {
    }

    /**
     * Current balance of one account, read from the ledger rather than stored, and
     * signed the way a reader expects it.
     *
     * <p>The ledger query returns debit minus credit, which is the natural sign for
     * assets and expenses but the negative of what anyone thinks of when they read
     * "payables" or "client credit". Normalising here means every figure on the
     * dashboard is positive when the firm is owed money and positive when the firm
     * owes money.
     */
    private BigDecimal balance(String accountCode) {
        Account account = accountingService.requireAccount(accountCode);
        BigDecimal debitMinusCredit = nz(journalLineRepository.accountBalance(
                TenantContext.getRequired(), account.getId()));
        return switch (account.getType()) {
            case ASSET, EXPENSE -> debitMinusCredit;
            case LIABILITY, EQUITY, REVENUE -> debitMinusCredit.negate();
        };
    }

    /**
     * Receivables already past their due date.
     *
     * <p>Read from invoice aging buckets rather than the AR account balance, because
     * "overdue" is a property of the invoice, not of the ledger account: the ledger
     * cannot tell a bill due in three months from one that was due last year.
     */
    private BigDecimal overdueReceivables(UUID tenantId, LocalDate asOf) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : invoiceRepository.arAgingBuckets(tenantId, OPEN_INVOICE_STATUSES, asOf)) {
            String bucket = String.valueOf(row[0]);
            if ("D1_30".equals(bucket)
                    || "D31_60".equals(bucket)
                    || "D61_90".equals(bucket)
                    || "D90_PLUS".equals(bucket)) {
                total = total.add(nz(toDecimal(row[2])));
            }
        }
        return total;
    }

    private int countReconciliationsInProgress() {
        return reconciliationRepository.countInProgress(TenantContext.getRequired());
    }

    private BigDecimal toDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : BigDecimal.ZERO;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}