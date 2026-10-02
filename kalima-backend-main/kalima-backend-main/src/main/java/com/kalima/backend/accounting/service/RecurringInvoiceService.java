package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.RecurringInvoiceCreateRequest;
import com.kalima.backend.accounting.dto.RecurringInvoiceResponse;
import com.kalima.backend.accounting.entity.RecurringInvoice;
import com.kalima.backend.accounting.repository.RecurringInvoiceRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.invoice.service.InvoiceService;
import com.kalima.backend.invoice.dto.InvoiceCreateRequest;
import com.kalima.backend.invoice.service.InvoiceService;
import com.kalima.backend.invoice.dto.InvoiceResponse;
import com.kalima.backend.invoice.service.InvoiceService;
import com.kalima.backend.invoice.dto.InvoiceStatusUpdateRequest;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Recurring invoice templates.
 *
 * <p>A template is a schedule, not an invoice: creating one posts nothing. This
 * service owns the schedule arithmetic and decides when a run is due. The invoice
 * itself is always created through {@link InvoiceService}, so a generated invoice is
 * posted and taxed by exactly the same rules as a hand-written one.
 */
@Service
public class RecurringInvoiceService {

    /** Terms assumed when a template does not name a payment date of its own. */
    private static final int DEFAULT_DUE_DAYS = 30;

private final RecurringInvoiceRepository recurringRepository;
    private final CaseFileRepository caseFileRepository;
    private final PersonRepository personRepository;
    private final InvoiceService invoiceService;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public RecurringInvoiceService(RecurringInvoiceRepository recurringRepository,
                                   CaseFileRepository caseFileRepository,
                                   PersonRepository personRepository,
                                   InvoiceService invoiceService,
                                   AccountingService accounting,
                                   AuditLogService auditLogService) {
        this.recurringRepository = recurringRepository;
        this.caseFileRepository = caseFileRepository;
        this.personRepository = personRepository;
        this.invoiceService = invoiceService;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<RecurringInvoiceResponse> list(UUID clientId) {
        UUID tenantId = TenantContext.getRequired();
        return (clientId == null
                ? recurringRepository.findAllByCompany(tenantId)
                : recurringRepository.findByCompanyIdAndClientId(tenantId, clientId))
                .stream().map(RecurringInvoiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RecurringInvoiceResponse get(UUID id) {
        return RecurringInvoiceResponse.from(load(id));
    }

    /** Templates whose next run has come around. */
    @Transactional(readOnly = true)
    public List<RecurringInvoiceResponse> due(LocalDate on) {
        LocalDate day = on == null ? LocalDate.now() : on;
        return recurringRepository.findDue(TenantContext.getRequired(), day)
                .stream().map(RecurringInvoiceResponse::from).toList();
    }

    @Transactional
    public RecurringInvoiceResponse create(@Valid RecurringInvoiceCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        Person client = personRepository.findScopedById(request.clientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "العميل غير موجود"));

        CaseFile caseFile = null;
        if (request.caseId() != null) {
            caseFile = caseFileRepository.findScopedById(request.caseId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "القضية غير موجودة"));
        }

        int interval = request.intervalCount() == null ? 1 : request.intervalCount();
        if (interval <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "الفترة يجب أن تكون أكبر من صفر");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "تاريخ النهاية لا يسبق تاريخ البداية");
        }

        BigDecimal subtotal = nz(request.subtotal());
        if (subtotal.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "يجب أن يكون الإجمالي قبل الضريبة أكبر من صفر");
        }
        BigDecimal taxRate = nz(request.taxRate());
        if (taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "نسبة الضريبة يجب أن تكون بين 0 و 100");
        }
        BigDecimal taxAmount = nz(request.taxAmount());
        if (taxAmount.signum() == 0 && taxRate.signum() > 0) {
            taxAmount = subtotal.multiply(taxRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        RecurringInvoice template = RecurringInvoice.of(accounting.currentTenantReference());
        template.setClient(client);
        template.setCaseFile(caseFile);
        template.setDescription(request.description());
        template.setFrequency(request.frequency());
        template.setIntervalCount(interval);
        template.setStartDate(request.startDate());
        template.setEndDate(request.endDate());
        template.setNextRunDate(request.startDate());
        template.setSubtotal(subtotal);
        template.setTaxRate(taxRate);
        template.setTaxAmount(taxAmount);
        template.setTotal(subtotal.add(taxAmount));
        template.setActive(true);
        template.setTimesGenerated(0);
        template.setNotes(request.notes());
        template.setCreatedBy(accounting.currentUser());

        RecurringInvoice saved = recurringRepository.save(template);
        auditLogService.recordSuccess("ACCOUNTING_RECURRING_TEMPLATE_CREATED", accounting.currentUser(),
                "RecurringInvoice", saved.getId(),
                java.util.Map.of("frequency", saved.getFrequency().name(),
                        "total", saved.getTotal().toPlainString()));
        return RecurringInvoiceResponse.from(saved);
    }

    /**
 * Issues the invoice for the period that is due and advances the schedule.
 *
 * <p>The invoice is created and issued here rather than left for someone to raise by
 * hand, because a schedule nobody runs is a promise the firm does not keep. It is
 * issued, not drafted: raising a draft would move nothing while quietly advancing
 * the template, skipping a billing period.
 *
 * <p>One run cannot be billed twice. The check on the last generated date is what
 * stops a retry, or a second click, from invoicing the same retainer month twice.
 */
    @Transactional
    public GenerationResult generate(UUID id, LocalDate runDate, LocalDate dueDate) {
        UUID tenantId = TenantContext.getRequired();
        RecurringInvoice template = recurringRepository
                .findForUpdateByCompanyIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "قالب الفاتورة المتكررة غير موجود"));
        if (!template.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "القالب غير نشط");
        }

        LocalDate run = runDate == null ? template.getNextRunDate() : runDate;
        if (template.getLastGeneratedDate() != null
                && !run.isAfter(template.getLastGeneratedDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "تم إصدار فاتورة لهذه الفترة في " + template.getLastGeneratedDate());
        }
        if (template.getEndDate() != null && run.isAfter(template.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "تاريخ التشغيل يتجاوز تاريخ انتهاء القالب");
        }

        String notes = "فاتورة متكررة: " + template.getDescription()
                + " (قالب " + template.getId() + ")";
        InvoiceResponse invoice = invoiceService.create(new InvoiceCreateRequest(
                template.getClient().getId(),
                template.getCaseFile() == null ? null : template.getCaseFile().getId(),
                run,
                dueDate == null ? run.plusDays(DEFAULT_DUE_DAYS) : dueDate,
                template.getSubtotal(),
                template.getTaxRate(),
                BigDecimal.ZERO,
                notes));
        invoiceService.updateStatus(invoice.id(),
                new InvoiceStatusUpdateRequest(InvoiceStatus.ISSUED));

        markGenerated(id, run);

        auditLogService.recordSuccess("ACCOUNTING_RECURRING_INVOICE_ISSUED", accounting.currentUser(),
                "Invoice", invoice.id(),
                java.util.Map.of("templateId", template.getId().toString(),
                        "invoiceNumber", invoice.invoiceNumber(),
                        "runDate", run.toString()));

        return new GenerationResult(template.getId(), invoice.id(), invoice.invoiceNumber(),
                run, template.getNextRunDate(), invoice.total());
    }

    public record GenerationResult(UUID templateId, UUID invoiceId, String invoiceNumber,
                                   LocalDate runDate, LocalDate nextRunDate,
                                   BigDecimal total) {
    }

    /**
     * Advances the schedule to the run after {@code runDate} and records that it
     * happened. Called only once the invoice for that run actually exists, so a
     * failed generation does not silently skip a billing period.
     */
    @Transactional
    public RecurringInvoiceResponse markGenerated(UUID id, LocalDate runDate) {
        RecurringInvoice template = load(id);
        if (!template.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "القالب غير نشط");
        }
        LocalDate run = runDate == null ? template.getNextRunDate() : runDate;

        template.setTimesGenerated(template.getTimesGenerated() + 1);
        template.setLastGeneratedDate(run);
        template.setNextRunDate(nextRun(template, run));

        // Stop the schedule once it would pass its own end date, so a monthly
        // template with an end date cannot keep billing into next year.
        if (template.getEndDate() != null
                && template.getNextRunDate().isAfter(template.getEndDate())) {
            template.setActive(false);
        }

        RecurringInvoice saved = recurringRepository.save(template);
        auditLogService.recordSuccess("ACCOUNTING_RECURRING_TEMPLATE_GENERATED", accounting.currentUser(),
                "RecurringInvoice", saved.getId(),
                java.util.Map.of("runDate", run.toString(),
                        "nextRunDate", saved.getNextRunDate().toString()));
        return RecurringInvoiceResponse.from(saved);
    }

    @Transactional
    public void deactivate(UUID id) {
        RecurringInvoice template = load(id);
        template.setActive(false);
        recurringRepository.save(template);
        auditLogService.recordSuccess("ACCOUNTING_RECURRING_TEMPLATE_DEACTIVATED", accounting.currentUser(),
                "RecurringInvoice", template.getId(), java.util.Map.of());
    }

    /**
     * Next date strictly after {@code run} for this schedule. Month arithmetic goes
     * through the first of the target month so a 31st start does not clamp down to
     * the 28th and then creep.
     */
    LocalDate nextRun(RecurringInvoice template, LocalDate run) {
        int interval = Math.max(1, template.getIntervalCount());
        LocalDate base = run.plusDays(1);
        return switch (template.getFrequency()) {
            case WEEKLY -> base.with(java.time.temporal.TemporalAdjusters.previousOrSame(
                            java.time.DayOfWeek.MONDAY)).plusWeeks(interval);
            case MONTHLY -> plusMonths(base, interval);
            case QUARTERLY -> plusMonths(base, 3L * interval);
            case YEARLY -> plusMonths(base, 12L * interval);
        };
    }

    private LocalDate plusMonths(LocalDate from, long months) {
        return from.plusMonths(months);
    }

    private RecurringInvoice load(UUID id) {
        return recurringRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "قالب الفاتورة المتكررة غير موجود"));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}