package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.entity.*;
import com.kalima.backend.accounting.repository.*;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.invoice.repository.InvoicePaymentRepository;
import com.kalima.backend.auth.support.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core double-entry posting engine. Every money movement in the system funnels
 * through {@link #post} so that debits always equal credits.
 */
@Service
public class AccountingService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingSequenceRepository sequenceRepository;
    private final BankAccountRepository bankAccountRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final CurrentUserService currentUserService;
    private final FinancialPeriodRepository periodRepository;
    private final ChartOfAccountsSeeder chartOfAccountsSeeder;
    private final AtomicSequenceDao sequenceDao;
    private final AuditLogService auditLogService;
    private final String baseCurrency;

    public AccountingService(AccountRepository accountRepository,
                             JournalEntryRepository journalEntryRepository,
                             AccountingSequenceRepository sequenceRepository,
                             BankAccountRepository bankAccountRepository,
                             ClientAccountRepository clientAccountRepository,
                             CompanyRepository companyRepository,
                             PersonRepository personRepository,
                             InvoicePaymentRepository invoicePaymentRepository,
                             CurrentUserService currentUserService,
                             FinancialPeriodRepository periodRepository,
                             ChartOfAccountsSeeder chartOfAccountsSeeder,
                             AtomicSequenceDao sequenceDao,
                             AuditLogService auditLogService,
                             @Value("${kalima.accounting.base-currency:SAR}") String baseCurrency) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.sequenceRepository = sequenceRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.clientAccountRepository = clientAccountRepository;
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
        this.currentUserService = currentUserService;
        this.periodRepository = periodRepository;
        this.chartOfAccountsSeeder = chartOfAccountsSeeder;
        this.sequenceDao = sequenceDao;
        this.auditLogService = auditLogService;
        this.baseCurrency = requireCurrencyCode(baseCurrency);
    }

    // ------------------------------------------------------------------
    // System account codes.
    //
    // The codes themselves are declared once in SystemAccountCatalog; these
    // constants only alias them so posting code never repeats a literal string.
    // V60 seeded the original list as SQL — it is immutable and left untouched.
    // ------------------------------------------------------------------
    public static final String BASE_CURRENCY = "SAR";
    public static final String ACC_CASH = "1100";
    public static final String ACC_BANK = "1110";
public static final String ACC_BANK_UNRECONCILED = "1160";
    public static final String ACC_AR = "1200";
    /** Billable work performed but not yet invoiced (WIP). */
    public static final String ACC_UNBILLED_AR = "1400";
    public static final String ACC_PREPAID = "1500";
    public static final String ACC_AP = "2100";
    public static final String ACC_VAT_PAYABLE = "2200";
    public static final String ACC_VAT_INPUT = "2210";
    /** Client money held on account — a liability until the work is performed. */
    public static final String ACC_RETAINER_LIABILITY = "2300";
    public static final String ACC_DEFERRED_REVENUE = "2400";
public static final String ACC_CLIENT_CREDIT = "2350";
public static final String ACC_SALES_RETURNS = "4110";
    public static final String ACC_CAPITAL = "3100";
    public static final String ACC_FEE_INCOME = "4100";
    public static final String ACC_OTHER_INCOME = "4900";
    public static final String ACC_GOV_FEES = "5600";
    public static final String ACC_OTHER_EXPENSE = "5950";
    public static final String ACC_SALARIES = "5100";
    public static final String ACC_RENT = "5200";
    public static final String ACC_UTILITIES = "5300";
    public static final String ACC_MARKETING = "5400";
    public static final String ACC_SOFTWARE = "5500";
    public static final String ACC_LEGAL_FEES = "5700";
    public static final String ACC_TRANSPORT = "5800";
    public static final String ACC_SUPPLIES = "5900";

    public Account requireAccount(String code) {
        UUID tenantId = TenantContext.getRequired();
        // Self-heal: a company provisioned before ChartOfAccountsSeeder existed (or
        // created by a path that bypassed it) must not be stuck with an empty chart.
        chartOfAccountsSeeder.ensureCurrentTenantSeeded();
        return accountRepository.findByCompany_IdAndCode(tenantId, code)
                .orElseThrow(() -> new IllegalStateException(
                        "الحساب " + code + " غير موجود في دليل الحسابات"));
    }

    public Account requireAccountById(UUID id) {
        return accountRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new IllegalArgumentException("الحساب غير موجود"));
    }

    public Person currentUser() {
        return currentUserService.requireCurrentPerson();
    }

    public Optional<BankAccount> findBankAccount(UUID id) {
        if (id == null) return Optional.empty();
        return bankAccountRepository.findByCompany_IdAndId(TenantContext.getRequired(), id);
    }

    /** Default cash account used when a caller does not name a bank account. */
    public Optional<BankAccount> defaultCashAccount() {
        return bankAccountRepository
                .findByCompany_IdOrderByNameArAsc(TenantContext.getRequired())
                .stream()
                .filter(BankAccount::isActive)
                .findFirst();
    }

    public Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    /**
     * Resolves a client tag through {@code findScopedById} so the tenant Hibernate
     * filter applies — Spring Data's default {@code findById} uses
     * {@code EntityManager#find}, which bypasses entity-level filters and would
     * happily return another company's person.
     */
    public Person requirePerson(UUID personId) {
        if (personId == null) return null;
        return personRepository.findScopedById(personId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الشخص غير موجود"));
    }

    /**
     * Client control accounts are created lazily the first time a client is
     * billed, so existing clients do not need a manual setup step before the
     * ledger can be used. Opening balance stays 0 because pre-existing
     * receivables live in the invoice table, not in a client row.
     */
    public ClientAccount ensureClientAccount(Person client) {
        if (client == null) return null;
        UUID tenantId = TenantContext.getRequired();
        return clientAccountRepository.findByCompany_IdAndPerson_Id(tenantId, client.getId())
                .orElseGet(() -> {
                    ClientAccount ca = new ClientAccount();
                    ca.setCompany(currentTenantReference());
                    ca.setPerson(client);
                    ca.setAccount(requireAccount(ACC_AR));
                    ca.setOpeningBalance(BigDecimal.ZERO);
                    return clientAccountRepository.save(ca);
                });
    }

    // ------------------------------------------------------------------
    // Numbering
    // ------------------------------------------------------------------
    /**
     * Reserves the next document number for the current tenant. The reservation is a
     * single {@code INSERT … ON CONFLICT … RETURNING} statement, so concurrent
     * postings can never receive the same number and a rolled-back entry never
     * releases a number another entry already used.
     */
    @Transactional
    public String nextNumber(AccountingSequence.Scope scope, LocalDate date) {
        UUID tenantId = TenantContext.getRequired();
        long value = sequenceDao.nextValue(tenantId, scope.name(), date.getYear());
        return AtomicSequenceDao.format(scope, date.getYear(), value);
    }

    // ------------------------------------------------------------------
    // Financial periods
    // ------------------------------------------------------------------

    /**
     * A date is postable when it either falls outside every configured period
     * (unconstrained) or lands in a period that is still OPEN. CLOSED/LOCKED
     * periods must reject new postings so reported figures stay stable.
     */
    @Transactional(readOnly = true)
    public boolean isPeriodOpen(LocalDate date) {
        return periodRepository.findByDate(TenantContext.getRequired(), date)
                .map(p -> p.getStatus() == FinancialPeriod.Status.OPEN)
                .orElse(true);
    }

    private void assertPeriodOpen(LocalDate date) {
        periodRepository.findByDate(TenantContext.getRequired(), date).ifPresent(p -> {
            if (p.getStatus() != FinancialPeriod.Status.OPEN) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "الفترة المالية " + p.getName() + " (" + p.getStatus() + ") مغلقة ولا تقبل الترحيل");
            }
        });
    }

    // ------------------------------------------------------------------
    // Posting
    // ------------------------------------------------------------------
    public record Line(BigDecimal debit, BigDecimal credit, Account account,
                       String description, Person client, BankAccount bankAccount,
                       CaseFile caseFile) {
        public Line(BigDecimal debit, BigDecimal credit, Account account,
                    String description, Person client, BankAccount bankAccount) {
            this(debit, credit, account, description, client, bankAccount, null);
        }
        public static Line dr(BigDecimal amount, Account account, String description) {
            return new Line(amount, BigDecimal.ZERO, account, description, null, null, null);
        }
        public static Line dr(BigDecimal amount, Account account, String description,
                              Person client, BankAccount bank) {
            return new Line(amount, BigDecimal.ZERO, account, description, client, bank, null);
        }
        public static Line cr(BigDecimal amount, Account account, String description) {
            return new Line(BigDecimal.ZERO, amount, account, description, null, null, null);
        }
        public static Line cr(BigDecimal amount, Account account, String description,
                              Person client, BankAccount bank) {
            return new Line(BigDecimal.ZERO, amount, account, description, client, bank, null);
        }
        /** Case-tagged variants, so case profitability can be read off the ledger. */
        public static Line drForCase(BigDecimal amount, Account account, String description,
                                     Person client, BankAccount bank, CaseFile caseFile) {
            return new Line(amount, BigDecimal.ZERO, account, description, client, bank, caseFile);
        }
        public static Line crForCase(BigDecimal amount, Account account, String description,
                                     Person client, BankAccount bank, CaseFile caseFile) {
            return new Line(BigDecimal.ZERO, amount, account, description, client, bank, caseFile);
        }
    }

    @Transactional
    public JournalEntry post(LocalDate date, String description, JournalEntry.Source source,
                             String referenceType, UUID referenceId, Person createdBy,
                             List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("القيد لا يمكن أن يكون فارغاً");
        }
        assertPeriodOpen(date);

        UUID tenantId = TenantContext.getRequired();
        boolean reversal = source == JournalEntry.Source.REVERSAL;
        List<Line> scopedLines = new ArrayList<>(lines.size());
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int lineNo = 1;
        for (Line l : lines) {
            if (l.account() == null || l.account().getId() == null) {
                throw new IllegalArgumentException("كل سطر يجب أن يرتبط بحساب صالح");
            }
            Account scopedAccount = accountRepository.findByCompany_IdAndId(tenantId, l.account().getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "الحساب غير موجود في الشركة الحالية"));
            if (!scopedAccount.isActive() && !reversal) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "لا يمكن الترحيل إلى حساب غير نشط: " + scopedAccount.getCode());
            }
            Person scopedClient = l.client() == null ? null : requirePerson(l.client().getId());
            BankAccount scopedBank = null;
            if (l.bankAccount() != null) {
                scopedBank = bankAccountRepository.findByCompany_IdAndId(tenantId, l.bankAccount().getId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "الحساب البنكي غير موجود في الشركة الحالية"));
                if (!scopedBank.isActive() && !reversal) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحساب البنكي موقوف");
                }
                if (!scopedBank.getAccount().getId().equals(scopedAccount.getId())) {
                    throw new IllegalArgumentException("الحساب البنكي لا يطابق الحساب المحاسبي للسطر");
                }
            }
            l = new Line(l.debit(), l.credit(), scopedAccount, l.description(), scopedClient, scopedBank);
            scopedLines.add(l);
            BigDecimal d = nvl(l.debit());
            BigDecimal c = nvl(l.credit());
            if (d.signum() < 0 || c.signum() < 0) {
                throw new IllegalArgumentException("لا يمكن أن يكون المبلغ سالباً");
            }
            // Exactly one side must carry a real amount. This mirrors
            // chk_journal_lines_amount so a violation is rejected with a readable
            // message instead of a constraint violation at flush time.
            if (d.signum() > 0 && c.signum() > 0) {
                throw new IllegalArgumentException("سطر القيد لا يكون مديناً ودائناً في نفس الوقت");
            }
            if (d.signum() == 0 && c.signum() == 0) {
                throw new IllegalArgumentException("سطر القيد لا يمكن أن يكون بصفر مدين وصفر دائن");
            }
            totalDebit = totalDebit.add(d);
            totalCredit = totalCredit.add(c);
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException(
                    "القيد غير متوازن: مدين " + totalDebit + " ≠ دائن " + totalCredit);
        }

        JournalEntry entry = new JournalEntry();
        entry.setCompany(currentTenantReference());
        entry.setEntryNumber(nextNumber(AccountingSequence.Scope.JOURNAL, date));
        entry.setEntryDate(date);
        entry.setDescription(description);
        entry.setSource(source);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setCreatedBy(createdBy);
        entry.setPosted(true);

        for (Line l : scopedLines) {
            JournalLine line = new JournalLine();
            line.setCompany(entry.getCompany());
            line.setLineNo(lineNo++);
            line.setAccount(l.account());
            line.setDebit(nvl(l.debit()));
            line.setCredit(nvl(l.credit()));
            line.setDescription(l.description());
            line.setClient(l.client());
            line.setBankAccount(l.bankAccount());
            line.setCaseFile(l.caseFile());
            entry.addLine(line);
        }
        entry.recalculateTotals();
        JournalEntry saved = journalEntryRepository.save(entry);
        auditLogService.recordSuccess("ACCOUNTING_JOURNAL_POSTED", createdBy,
            "JournalEntry", saved.getId(), java.util.Map.of(
                "entryNumber", saved.getEntryNumber(),
                "source", saved.getSource().name(),
                "totalDebit", saved.getTotalDebit().toPlainString(),
                "totalCredit", saved.getTotalCredit().toPlainString()));
        return saved;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String str(String v) {
        return v == null ? "" : v;
    }

    /** Reverses a posted entry by posting its mirror image. */
    @Transactional
    public JournalEntry reverse(JournalEntry original, Person createdBy) {
        if (!original.isPosted()) {
            throw new IllegalArgumentException("القيد غير مُرحّل");
        }
        if (original.getReversedEntry() != null) {
            throw new IllegalArgumentException("تم عكس هذا القيد مسبقاً");
        }
        List<Line> mirror = original.getLines().stream()
                .map(l -> l.getDebit().signum() > 0
                        ? Line.cr(l.getDebit(), l.getAccount(), "عكس: " + str(l.getDescription()),
                                l.getClient(), l.getBankAccount())
                        : Line.dr(l.getCredit(), l.getAccount(), "عكس: " + str(l.getDescription()),
                                l.getClient(), l.getBankAccount()))
                .toList();

        JournalEntry reversal = post(
                original.getEntryDate(),
                "عكس القيد " + original.getEntryNumber(),
                JournalEntry.Source.REVERSAL,
                "JOURNAL_ENTRY",
                original.getId(),
                createdBy,
                mirror);
        original.setReversedEntry(reversal);
        journalEntryRepository.save(original);
        auditLogService.recordSuccess("ACCOUNTING_JOURNAL_REVERSED", createdBy,
            "JournalEntry", original.getId(), java.util.Map.of(
                "originalEntryNumber", original.getEntryNumber(),
                "reversalEntryId", reversal.getId().toString(),
                "reversalEntryNumber", reversal.getEntryNumber()));
        return reversal;
    }

    public String normalizeCurrency(String raw) {
        if (!StringUtils.hasText(raw)) return baseCurrency;
        String v = raw.trim().toUpperCase();
        if (v.length() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "رمز العملة يجب أن يتكون من 3 أحرف");
        }
        return requireBaseCurrency(v);
    }

    /**
     * The ledger is single-currency (SAR) with no FX translation, so anything
     * else is rejected rather than silently coerced.
     */
    public String requireBaseCurrency(String raw) {
        if (!StringUtils.hasText(raw)) return baseCurrency;
        String v = raw.trim().toUpperCase();
        if (!baseCurrency.equals(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "النظام يعمل بعملة أساسية واحدة فقط (" + baseCurrency + ") ولا يدعم التحويل بين العملات");
        }
        return baseCurrency;
    }

    public String baseCurrency() {
        return baseCurrency;
    }

    private static String requireCurrencyCode(String raw) {
        String value = raw == null ? BASE_CURRENCY : raw.trim().toUpperCase();
        if (value.length() != 3) {
            throw new IllegalArgumentException("kalima.accounting.base-currency must be a 3-letter code");
        }
        return value;
    }

    /**
     * Idempotency guard for auto-posting: a business document (invoice, expense,
     * receipt) must map to at most one journal entry, otherwise a status flip or
     * retry would double-count it.
     */
    @Transactional(readOnly = true)
    public boolean hasJournalEntry(UUID companyId, String referenceType, UUID referenceId) {
        if (referenceId == null) return false;
        return journalEntryRepository.existsByCompany_IdAndReferenceTypeAndReferenceId(
                companyId, referenceType, referenceId);
    }

    /**
     * Same guard but ignores entries that were already reversed, so a document
     * that is re-issued after a cancellation gets a fresh entry. Reversals are
     * stored under a JOURNAL_ENTRY reference, hence the entry-level check.
     */
    @Transactional(readOnly = true)
    public boolean hasLiveJournalEntry(UUID companyId, String referenceType, UUID referenceId) {
        if (referenceId == null) return false;
        return journalEntryRepository.findByReference(companyId, referenceType, referenceId).stream()
                .anyMatch(je -> je.isPosted() && je.getReversedEntry() == null);
    }
}
