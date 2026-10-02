package com.kalima.backend.invoice.service;

import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.repository.AtomicSequenceDao;
import com.kalima.backend.accounting.repository.JournalEntryRepository;
import com.kalima.backend.accounting.service.AccountingService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.invoice.dto.InvoiceCreateRequest;
import com.kalima.backend.invoice.dto.InvoiceListItemResponse;
import com.kalima.backend.invoice.dto.InvoicePaymentRequest;
import com.kalima.backend.invoice.dto.InvoicePaymentResponse;
import com.kalima.backend.invoice.dto.InvoiceResponse;
import com.kalima.backend.invoice.dto.InvoiceStatisticsResponse;
import com.kalima.backend.invoice.dto.InvoiceStatusUpdateRequest;
import com.kalima.backend.invoice.entity.Invoice;
import com.kalima.backend.invoice.entity.InvoicePayment;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import com.kalima.backend.invoice.repository.InvoicePaymentRepository;
import com.kalima.backend.invoice.repository.InvoiceRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final CurrentUserService currentUserService;
    private final CompanyRepository companyRepository;
    private final CaseFileRepository caseFileRepository;
    private final AccountingService accounting;
    private final JournalEntryRepository journalEntryRepository;
    private final AtomicSequenceDao sequenceDao;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoicePaymentRepository paymentRepository,
                          CurrentUserService currentUserService,
                          CompanyRepository companyRepository,
                          CaseFileRepository caseFileRepository,
                          AccountingService accounting,
                          JournalEntryRepository journalEntryRepository,
                          AtomicSequenceDao sequenceDao) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.currentUserService = currentUserService;
        this.companyRepository = companyRepository;
        this.caseFileRepository = caseFileRepository;
        this.accounting = accounting;
        this.journalEntryRepository = journalEntryRepository;
        this.sequenceDao = sequenceDao;
    }

    public InvoiceResponse create(InvoiceCreateRequest request) {
        Person recordedBy = currentUserService.requireCurrentPerson();
        Company company = currentTenantReference();

        BigDecimal taxRate = request.taxRate() == null ? BigDecimal.ZERO : request.taxRate();
        BigDecimal discount = request.discount() == null ? BigDecimal.ZERO : request.discount();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "نسبة الضريبة يجب أن تكون بين 0 و 100");
        }
        if (discount.signum() < 0 || discount.compareTo(request.subtotal()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "الخصم لا يمكن أن يكون سالباً أو أكبر من الإجمالي قبل الضريبة");
        }
        if (request.subtotal().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "إجمالي الفاتورة قبل الضريبة يجب أن يكون أكبر من صفر");
        }

        BigDecimal taxAmount = request.subtotal()
                .multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = request.subtotal().subtract(discount).add(taxAmount);

        Invoice invoice = new Invoice();
        invoice.setCompany(company);
        invoice.setRecordedBy(recordedBy);
        // Resolved through the tenant-scoped lookups: a client or case belonging to
        // another company must never be attachable to this invoice.
        invoice.setClient(accounting.requirePerson(request.clientId()));
        if (request.caseId() != null) {
            invoice.setCaseFile(requireCaseFile(request.caseId()));
        }
        invoice.setInvoiceNumber(nextInvoiceNumber(company));
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setSubtotal(request.subtotal());
        invoice.setTaxRate(taxRate);
        invoice.setTaxAmount(taxAmount);
        invoice.setDiscount(discount);
        invoice.setTotal(total);
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setNotes(request.notes());

        return toResponse(invoiceRepository.save(invoice));
    }

    /** Tenant-scoped case lookup; a foreign case id must read as "not found". */
    private CaseFile requireCaseFile(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "القضية غير موجودة"));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceListItemResponse> list(UUID clientId, InvoiceStatus status,
                                             String search, Pageable pageable) {
        String normalized = StringUtils.hasText(search) ? search.trim() : null;
        return invoiceRepository
                .search(TenantContext.getRequired(), clientId, status, normalized, pageable)
                .map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) {
        return toResponse(loadInvoice(id));
    }

    /**
     * Transitions a user may request explicitly.
     *
     * <p>PARTIALLY_PAID and PAID are deliberately absent: they describe money that was
     * actually allocated, so they are only ever produced by {@link #recordPayment}.
     * Previously any status could be PATCHed, which let a caller mark an unpaid
     * invoice PAID (removing it from AR aging while the ledger still showed the
     * debt) or push a paid invoice back to DRAFT and leave posted revenue behind.
     */
    private static final Map<InvoiceStatus, Set<InvoiceStatus>> MANUAL_TRANSITIONS = Map.of(
            InvoiceStatus.DRAFT, Set.of(InvoiceStatus.ISSUED, InvoiceStatus.CANCELLED),
            InvoiceStatus.ISSUED, Set.of(InvoiceStatus.OVERDUE, InvoiceStatus.CANCELLED),
            InvoiceStatus.OVERDUE, Set.of(InvoiceStatus.ISSUED, InvoiceStatus.CANCELLED),
            InvoiceStatus.PARTIALLY_PAID, Set.of(),
            InvoiceStatus.PAID, Set.of(),
            InvoiceStatus.CANCELLED, Set.of());

    public InvoiceResponse updateStatus(UUID id, InvoiceStatusUpdateRequest request) {
        Invoice invoice = loadInvoiceForUpdate(id);
        InvoiceStatus current = invoice.getStatus();
        InvoiceStatus target = request.status();

        if (target == current) {
            return toResponse(invoice);
        }
        if (target == InvoiceStatus.PARTIALLY_PAID || target == InvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "حالة " + target + " تُشتق من الدفعات المسجّلة؛ سجّل دفعة بدلاً من تغيير الحالة يدوياً");
        }
        Set<InvoiceStatus> allowed = MANUAL_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن نقل الفاتورة من " + current + " إلى " + target);
        }

        if (target == InvoiceStatus.CANCELLED) {
            BigDecimal paid = nvl(invoice.getAmountPaid());
            if (paid.signum() > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "لا يمكن إلغاء فاتورة عليها دفعات مسجّلة؛ يجب تسوية الدفعات أولاً");
            }
            reverseRevenueRecognition(invoice);
        }
        if (target == InvoiceStatus.ISSUED) {
            // Re-issuing a cancelled invoice must not resurrect the old entry id, and
            // must recognise revenue again exactly once.
            reverseCancelledRevenueIfNeeded(invoice);
        }

        invoice.setStatus(target);
        Invoice saved = invoiceRepository.save(invoice);
        if (isRevenueStatus(target)) {
            postRevenueRecognition(saved);
        }
        return toResponse(saved);
    }

    /**
     * Status implied by the money actually allocated to the invoice. This is the only
     * place payment-derived statuses are produced, so the stored status can never
     * disagree with the payment rows.
     */
    private InvoiceStatus derivePaymentStatus(Invoice invoice) {
        BigDecimal paid = nvl(invoice.getAmountPaid());
        BigDecimal total = nvl(invoice.getTotal());
        if (paid.signum() <= 0) {
            return invoice.getStatus() == InvoiceStatus.OVERDUE
                    ? InvoiceStatus.OVERDUE
                    : InvoiceStatus.ISSUED;
        }
        if (paid.compareTo(total) >= 0) {
            return InvoiceStatus.PAID;
        }
        return InvoiceStatus.PARTIALLY_PAID;
    }

    private void reverseCancelledRevenueIfNeeded(Invoice invoice) {
        UUID tenantId = TenantContext.getRequired();
        journalEntryRepository.findByReference(tenantId, "INVOICE", invoice.getId()).stream()
                .filter(je -> je.isPosted() && je.getReversedEntry() == null)
                .findFirst()
                .ifPresent(je -> accounting.reverse(je, currentUserService.requireCurrentPerson()));
    }

    /**
     * Statuses that mean the invoice is no longer a draft and therefore has to
     * hit the ledger. DRAFT and CANCELLED deliberately post nothing.
     */
    private boolean isRevenueStatus(InvoiceStatus status) {
        return status == InvoiceStatus.ISSUED
                || status == InvoiceStatus.PARTIALLY_PAID
                || status == InvoiceStatus.PAID
                || status == InvoiceStatus.OVERDUE;
    }

    /** Cancelling an issued invoice must take the recognised revenue back out. */
    private void reverseRevenueRecognition(Invoice invoice) {
        UUID tenantId = TenantContext.getRequired();
        journalEntryRepository.findByReference(tenantId, "INVOICE", invoice.getId()).stream()
                .filter(je -> je.isPosted() && je.getReversedEntry() == null)
                .findFirst()
                .ifPresent(je -> accounting.reverse(je, currentUserService.requireCurrentPerson()));
    }

    /**
     * Dr Accounts Receivable / Cr Fee Income + Cr VAT Payable. Guarded by the
     * (referenceType, referenceId) pair so status flips can never double-post.
     */
    private void postRevenueRecognition(Invoice invoice) {
        UUID tenantId = TenantContext.getRequired();
        if (invoice.getTotal().signum() <= 0) return;
        if (accounting.hasLiveJournalEntry(tenantId, "INVOICE", invoice.getId())) return;
        accounting.ensureClientAccount(invoice.getClient());

        BigDecimal net = invoice.getSubtotal().subtract(invoice.getDiscount());
        BigDecimal vat = invoice.getTaxAmount();
        String description = "تحقق إيراد " + invoice.getInvoiceNumber();

        List<AccountingService.Line> lines = new ArrayList<>();
        lines.add(AccountingService.Line.dr(invoice.getTotal(),
                accounting.requireAccount(AccountingService.ACC_AR), description,
                invoice.getClient(), null));
        if (net.signum() != 0) {
            lines.add(AccountingService.Line.cr(net,
                    accounting.requireAccount(AccountingService.ACC_FEE_INCOME), description));
        }
        if (vat.signum() != 0) {
            lines.add(AccountingService.Line.cr(vat,
                    accounting.requireAccount(AccountingService.ACC_VAT_PAYABLE), "ضريبة القيمة المضافة"));
        }
        accounting.post(invoice.getIssueDate(), description,
                JournalEntry.Source.INVOICE, "INVOICE", invoice.getId(),
                currentUserService.requireCurrentPerson(), lines);
    }

    public InvoiceResponse recordPayment(UUID id, InvoicePaymentRequest request) {
        Invoice invoice = loadInvoiceForUpdate(id);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن تسجيل دفعة على فاتورة مسودة؛ يجب إصدار الفاتورة أولاً");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن تسجيل دفعة على فاتورة ملغاة");
        }
        // Legacy or manually flipped invoices may be issued without a revenue
        // entry; recognise it now so the payment cannot credit an empty AR leg.
        postRevenueRecognition(invoice);

        BigDecimal newPaid = invoice.getAmountPaid().add(request.amount());
        if (newPaid.compareTo(invoice.getTotal()) > 0) {
            // Overpayment is refused rather than silently absorbed: there is no client
            // credit-balance ledger yet, so an excess payment would leave the AR control
            // account negative with nothing to explain it.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "مبلغ الدفعة يتجاوز المتبقي على الفاتورة ("
                            + invoice.getTotal().subtract(invoice.getAmountPaid()) + ")");
        }

        BankAccount bank = request.bankAccountId() == null
                ? accounting.defaultCashAccount()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "لا يوجد حساب نقدي مُعرَّف لهذه الشركة"))
                : accounting.findBankAccount(request.bankAccountId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "الحساب البنكي غير موجود"));

        invoice.setAmountPaid(newPaid);
        invoice.setStatus(derivePaymentStatus(invoice));
        Invoice saved = invoiceRepository.save(invoice);

        InvoicePayment payment = new InvoicePayment();
        payment.setCompany(currentTenantReference());
        payment.setInvoice(saved);
        payment.setAmount(request.amount());
        payment.setPaymentDate(request.paymentDate());
        payment.setMethod(request.method());
        payment.setBankAccount(bank);
        payment.setReference(request.reference());
        payment.setNotes(request.notes());
        payment.setRecordedBy(currentUserService.requireCurrentPerson());
        paymentRepository.save(payment);

        String description = "تحصيل " + saved.getInvoiceNumber();
        JournalEntry entry = accounting.post(request.paymentDate(), description,
                JournalEntry.Source.PAYMENT, "INVOICE_PAYMENT", payment.getId(),
                currentUserService.requireCurrentPerson(),
                List.of(
                        AccountingService.Line.dr(request.amount(), bank.getAccount(), description,
                                saved.getClient(), bank),
                        AccountingService.Line.cr(request.amount(),
                                accounting.requireAccount(AccountingService.ACC_AR), description,
                                saved.getClient(), null)));
        payment.setJournalEntry(entry);
        paymentRepository.save(payment);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceStatisticsResponse statistics() {
        List<Invoice> invoices = invoiceRepository.findByCompany_Id(TenantContext.getRequired());
        // DRAFT and CANCELLED invoices carry no ledger entry, so counting them as
        // "billed" made this screen disagree with revenue recognised in the P&L.
        List<Invoice> recognised = invoices.stream()
                .filter(i -> isRevenueStatus(i.getStatus()))
                .toList();
        BigDecimal billed = recognised.stream()
                .map(Invoice::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = recognised.stream()
                .map(Invoice::getAmountPaid)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long paid = recognised.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .count();
        return new InvoiceStatisticsResponse(invoices.size(), (int) paid,
                billed, collected, billed.subtract(collected));
    }

    private Invoice loadInvoice(UUID id) {
        return invoiceRepository
                .findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "invoice not found"));
    }

    private Invoice loadInvoiceForUpdate(UUID id) {
        return invoiceRepository.findForUpdateByCompanyIdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "invoice not found"));
    }

    private String nextInvoiceNumber(Company company) {
        int year = java.time.Year.now().getValue();
        // Atomic: the old read-modify-write let two concurrent invoices take the same
        // number, and V63 scoped the uniqueness to (company_id, invoice_number).
        long next = sequenceDao.nextInvoiceValue(company.getId(), year);
        return "INV-%04d-%05d".formatted(year, next);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        Person client = invoice.getClient();
        Person recordedBy = invoice.getRecordedBy();
        CaseFile caseFile = invoice.getCaseFile();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                invoice.getCompany().getId(),
                client == null ? null : client.getId(),
                client == null ? null : client.getFullName(),
                caseFile == null ? null : caseFile.getId(),
                caseFile == null ? null : caseFile.getCaseNumber(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getSubtotal(),
                invoice.getTaxRate(),
                invoice.getTaxAmount(),
                invoice.getDiscount(),
                invoice.getTotal(),
                invoice.getAmountPaid(),
                invoice.getTotal().subtract(invoice.getAmountPaid()),
                invoice.getNotes(),
                recordedBy == null ? null : recordedBy.getFullName(),
                recordedBy == null ? null : recordedBy.getEmail(),
                invoice.getCreatedAt(),
                revenueEntryId(invoice),
                revenueEntryNumber(invoice),
                paymentRepository.findByInvoice_IdOrderByPaymentDateAsc(invoice.getId())
                        .stream().map(this::toPaymentResponse).toList());
    }

    /** The invoice is linked to its revenue entry through (referenceType, referenceId). */
    private java.util.Optional<com.kalima.backend.accounting.entity.JournalEntry> revenueEntry(Invoice invoice) {
        return journalEntryRepository.findByReference(
                        invoice.getCompany().getId(), "INVOICE", invoice.getId()).stream()
                .filter(je -> je.isPosted() && je.getReversedEntry() == null)
                .findFirst();
    }

    private UUID revenueEntryId(Invoice invoice) {
        return revenueEntry(invoice).map(com.kalima.backend.accounting.entity.JournalEntry::getId).orElse(null);
    }

    private String revenueEntryNumber(Invoice invoice) {
        return revenueEntry(invoice).map(com.kalima.backend.accounting.entity.JournalEntry::getEntryNumber).orElse(null);
    }

    private InvoicePaymentResponse toPaymentResponse(InvoicePayment p) {
        return new InvoicePaymentResponse(
                p.getId(), p.getInvoice().getId(), p.getAmount(), p.getPaymentDate(), p.getMethod(),
                p.getBankAccount() == null ? null : p.getBankAccount().getId(),
                p.getBankAccount() == null ? null : p.getBankAccount().getNameAr(),
                p.getReference(), p.getNotes(),
                p.getJournalEntry() == null ? null : p.getJournalEntry().getId(),
                p.getJournalEntry() == null ? null : p.getJournalEntry().getEntryNumber(),
                p.getRecordedBy() == null ? null : p.getRecordedBy().getFullName(),
                p.getCreatedAt() == null ? null
                        : LocalDate.ofInstant(p.getCreatedAt(), java.time.ZoneId.systemDefault()));
    }

    private InvoiceListItemResponse toListItem(Invoice invoice) {
        Person client = invoice.getClient();
        CaseFile caseFile = invoice.getCaseFile();
        return new InvoiceListItemResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                client == null ? null : client.getFullName(),
                caseFile == null ? null : caseFile.getCaseNumber(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getTotal(),
                invoice.getAmountPaid(),
                invoice.getTotal().subtract(invoice.getAmountPaid()));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }
}
