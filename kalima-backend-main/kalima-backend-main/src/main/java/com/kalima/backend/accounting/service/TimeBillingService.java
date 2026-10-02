package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.TimeBillingRequest;
import com.kalima.backend.accounting.dto.TimeEntryCreateRequest;
import com.kalima.backend.accounting.dto.TimeEntryResponse;
import com.kalima.backend.accounting.entity.AccountingSequence;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.entity.TimeEntry;
import com.kalima.backend.accounting.repository.TimeEntryRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.invoice.service.InvoiceService;
import com.kalima.backend.invoice.dto.InvoiceCreateRequest;
import com.kalima.backend.invoice.dto.InvoiceResponse;
import com.kalima.backend.invoice.dto.InvoiceStatusUpdateRequest;
import com.kalima.backend.invoice.entity.Invoice;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import com.kalima.backend.invoice.repository.InvoiceRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Time tracking.
 *
 * <p>Recording work moves the cost out of deferred revenue into unbilled
 * receivables: {@code Dr 1400 / Cr 2400}. Nothing is billed yet, so no revenue and
 * no AR are recognised here. {@link #bill} is what turns unbilled work into an
 * invoice and clears 1400 against 2400, which keeps that reversal in exactly one
 * place instead of splitting it between time entries and the invoice.
 */
@Service
public class TimeBillingService {

    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

    private final TimeEntryRepository timeEntryRepository;
    private final CaseFileRepository caseFileRepository;
    private final PersonRepository personRepository;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public TimeBillingService(TimeEntryRepository timeEntryRepository,
                              CaseFileRepository caseFileRepository,
                              PersonRepository personRepository,
                              InvoiceService invoiceService,
                              InvoiceRepository invoiceRepository,
                              AccountingService accounting,
                              AuditLogService auditLogService) {
        this.timeEntryRepository = timeEntryRepository;
        this.caseFileRepository = caseFileRepository;
        this.personRepository = personRepository;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<TimeEntryResponse> list(UUID caseId, UUID lawyerId, LocalDate from, LocalDate to) {
        return timeEntryRepository.search(TenantContext.getRequired(), caseId, lawyerId, from, to)
                .stream().map(TimeEntryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TimeEntryResponse get(UUID id) {
        return TimeEntryResponse.from(load(id));
    }

    /** Unbilled billable work on a case, which is what a timesheet invoice would draw on. */
    @Transactional(readOnly = true)
    public CaseWorkSummary summary(UUID caseId) {
        UUID tenantId = TenantContext.getRequired();
        BigDecimal unbilled = nz(timeEntryRepository.sumUnbilledByCase(tenantId, caseId));
        BigDecimal total = nz(timeEntryRepository.sumTotalByCase(tenantId, caseId));
        List<TimeEntryResponse> entries = timeEntryRepository
                .search(tenantId, caseId, null, null, null).stream()
                .map(TimeEntryResponse::from).toList();
        return new CaseWorkSummary(caseId, total, unbilled,
                total.subtract(unbilled), entries);
    }

    public record CaseWorkSummary(UUID caseId, BigDecimal totalWorkValue,
                                  BigDecimal unbilledValue, BigDecimal billedValue,
                                  List<TimeEntryResponse> entries) {
    }

    @Transactional
    public TimeEntryResponse create(@Valid TimeEntryCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        if (request.caseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "يجب تحديد القضية");
        }
        CaseFile caseFile = caseFileRepository.findScopedById(request.caseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "القضية غير موجودة"));

        LocalDate date = request.workDate() == null ? LocalDate.now() : request.workDate();
        boolean billable = request.billable() == null || request.billable();
        BigDecimal rate = nz(request.hourlyRate());

        TimeEntry entry = TimeEntry.of(accounting.currentTenantReference());
        entry.setEntryNumber(accounting.nextNumber(AccountingSequence.Scope.TIME_ENTRY, date));
        entry.setCaseFile(caseFile);
        entry.setLawyer(resolveLawyer(request.lawyerId()));
        entry.setWorkDate(date);
        entry.setDurationMinutes(request.durationMinutes());
        entry.setDescription(request.description());
        entry.setBillable(billable);
        entry.setHourlyRate(rate);
        // Non-billable work still costs the firm, so it is valued for profitability
        // but excluded from the unbilled balance an invoice can draw on.
        entry.setAmount(computeAmount(request.durationMinutes(), rate));
        entry.setCreatedBy(accounting.currentUser());

        String description = "تسجيل وقت عمل على القضية";
        List<AccountingService.Line> lines = List.of(
                AccountingService.Line.drForCase(entry.getAmount(),
                        accounting.requireAccount(AccountingService.ACC_UNBILLED_AR),
                        description, null, null, caseFile),
                AccountingService.Line.crForCase(entry.getAmount(),
                        accounting.requireAccount(AccountingService.ACC_DEFERRED_REVENUE),
                        description, null, null, caseFile));

        JournalEntry je = accounting.post(date, description, JournalEntry.Source.TIME_BILLING,
                "TIME_ENTRY", null, accounting.currentUser(), lines);
        entry.setJournalEntry(je);
        TimeEntry saved = timeEntryRepository.save(entry);
        je.setReferenceId(saved.getId());

        auditLogService.recordSuccess("ACCOUNTING_TIME_ENTRY_RECORDED", accounting.currentUser(),
                "TimeEntry", saved.getId(),
                java.util.Map.of("entryNumber", saved.getEntryNumber(),
                        "minutes", String.valueOf(saved.getDurationMinutes()),
                        "amount", saved.getAmount().toPlainString()));
        return TimeEntryResponse.from(saved);
    }

/**
     * Bills everything still unbilled on a case onto one invoice.
     *
     * <p>Two entries land together, and both are needed for the books to close: the
     * invoice recognises revenue and receivable
     * ({@code Dr 1200 / Cr 4100 [+ Cr 2200]}), and this service reverses the accrual
     * the time entries made ({@code Dr 1400 / Cr 2400}). Without the second one the
     * work would be counted twice, once as deferred and once as earned.
     *
     * <p>The invoice is issued rather than left as a draft, because the entries are
     * linked to it: a draft nobody sent would already have moved the ledger.
     */
    @Transactional
    public BillingResult bill(UUID caseId, @Valid TimeBillingRequest request) {
        UUID tenantId = TenantContext.getRequired();
        CaseFile caseFile = caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "القضية غير موجودة"));

        List<TimeEntry> unbilled = timeEntryRepository.findUnbilledForUpdate(tenantId, caseId);
        if (unbilled.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يوجد عمل غير مفوتر على هذه القضية");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (TimeEntry entry : unbilled) {
            subtotal = subtotal.add(entry.getAmount());
        }
        if (subtotal.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "قيمة العمل غير المفوتر أكبر من صفر");
        }

LocalDate issueDate = request.issueDate() == null ? LocalDate.now() : request.issueDate();
        String notes = request.notes() == null || request.notes().isBlank()
                ? "فاتورة عمل مسجلة من قيد الوقت"
                : request.notes();

        InvoiceResponse invoice = invoiceService.create(new InvoiceCreateRequest(
                clientForCase(caseFile, request.clientId()),
                caseId,
                issueDate,
                request.dueDate(),
                subtotal,
                request.taxRate(),
                BigDecimal.ZERO,
                notes));
        invoiceService.updateStatus(invoice.id(),
                new InvoiceStatusUpdateRequest(InvoiceStatus.ISSUED));

        String description = "إفراج عن عمل غير مفوتر للفاتورة " + invoice.invoiceNumber();
        accounting.post(issueDate, description, JournalEntry.Source.TIME_BILLING,
                "TIME_BILLING", invoice.id(), accounting.currentUser(),
                List.of(
                        AccountingService.Line.crForCase(subtotal,
                                accounting.requireAccount(AccountingService.ACC_UNBILLED_AR),
                                description, null, null, caseFile),
                        AccountingService.Line.drForCase(subtotal,
                                accounting.requireAccount(AccountingService.ACC_DEFERRED_REVENUE),
                                description, null, null, caseFile)));

        Invoice entity = invoiceRepository.findByCompany_IdAndId(tenantId, invoice.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الفاتورة غير موجودة"));
        for (TimeEntry entry : unbilled) {
            entry.setInvoice(entity);
            timeEntryRepository.save(entry);
        }

        auditLogService.recordSuccess("ACCOUNTING_CASE_BILLED", accounting.currentUser(),
                "Invoice", invoice.id(),
                java.util.Map.of("invoiceNumber", invoice.invoiceNumber(),
                        "entries", String.valueOf(unbilled.size()),
                        "subtotal", subtotal.toPlainString()));

        return new BillingResult(invoice.id(), invoice.invoiceNumber(), invoice.total(),
                subtotal, unbilled.size());
    }

public record BillingResult(UUID invoiceId, String invoiceNumber,
                                BigDecimal total, BigDecimal subtotal,
                                int billedEntries) {
    }

    /**
     * Work recorded on a case belongs to that case's client. Defaulting to the case
     * client keeps the invoice honest, and a mismatched client is refused outright:
     * billing one client's hours to another client makes the AR ageing and the case
     * ledger disagree with each other, and nothing downstream can detect it.
     */
    private UUID clientForCase(CaseFile caseFile, UUID requestedClientId) {
        Person caseClient = caseFile.getClient();
        if (caseClient == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "القضية غير مرتبطة بعميل؛ لا يمكن فوترتها");
        }
        if (requestedClientId == null) {
            return caseClient.getId();
        }
        if (!requestedClientId.equals(caseClient.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "العميل المحدد لا يطابق عميل القضية");
        }
        return requestedClientId;
    }

    /** Voids by reversal: the work stays on the record, the ledger stays balanced. */
    @Transactional
    public void voidEntry(UUID id) {
        TimeEntry entry = load(id);
        if (entry.getVoidedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "قيد الوقت ملغى بالفعل");
        }
        if (entry.getInvoice() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن إلغاء قيد وقت مرتبط بفاتورة؛ ألغِ الفاتورة بدلاً من ذلك");
        }
        accounting.reverse(entry.getJournalEntry(), accounting.currentUser());
        entry.setVoidedAt(Instant.now());
        timeEntryRepository.save(entry);
        auditLogService.recordSuccess("ACCOUNTING_TIME_ENTRY_VOIDED", accounting.currentUser(),
                "TimeEntry", entry.getId(), java.util.Map.of());
    }

    private BigDecimal computeAmount(int minutes, BigDecimal hourlyRate) {
        return hourlyRate.multiply(BigDecimal.valueOf(minutes))
                .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
    }

    private Person resolveLawyer(UUID lawyerId) {
        if (lawyerId == null) return accounting.currentUser();
        return personRepository.findScopedById(lawyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "المحامي غير موجود"));
    }

    private TimeEntry load(UUID id) {
        return timeEntryRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "قيد الوقت غير موجود"));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}