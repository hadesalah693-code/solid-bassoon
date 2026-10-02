package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.RefundCreateRequest;
import com.kalima.backend.accounting.dto.RefundResponse;
import com.kalima.backend.accounting.entity.AccountingSequence;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.entity.Refund;
import com.kalima.backend.accounting.repository.BankAccountRepository;
import com.kalima.backend.accounting.repository.RefundRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.invoice.entity.Invoice;
import com.kalima.backend.invoice.repository.InvoiceRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client refunds.
 *
 * <p>Money goes back out of the bank, so the offsetting debit has to come from
 * somewhere real: AR when the refund is against an invoice (it was AR that was
 * relieved when the client paid), otherwise unapplied client credit (2350), which is
 * where an unallocated receipt parks. Refunding against AR therefore permanently
 * reduces what the client owes rather than leaving the invoice short of cash it
 * never received.
 */
@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final InvoiceRepository invoiceRepository;
    private final BankAccountRepository bankAccountRepository;
    private final PersonRepository personRepository;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public RefundService(RefundRepository refundRepository,
                         InvoiceRepository invoiceRepository,
                         BankAccountRepository bankAccountRepository,
                         PersonRepository personRepository,
                         AccountingService accounting,
                         AuditLogService auditLogService) {
        this.refundRepository = refundRepository;
        this.invoiceRepository = invoiceRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.personRepository = personRepository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> list(UUID clientId, UUID invoiceId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusYears(1) : from;
        return refundRepository.search(TenantContext.getRequired(), clientId, invoiceId, start, end)
                .stream().map(RefundResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RefundResponse get(UUID id) {
        return RefundResponse.from(load(id));
    }

    @Transactional
    public RefundResponse create(@Valid RefundCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate date = request.refundDate() == null ? LocalDate.now() : request.refundDate();
        BigDecimal amount = request.amount();

        Invoice invoice = null;
        if (request.invoiceId() != null) {
            invoice = invoiceRepository.findForUpdateByCompanyIdAndId(tenantId, request.invoiceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الفاتورة غير موجودة"));
            // Capped by what the invoice was actually paid, so a refund can never
            // exceed the cash that came in against it.
            BigDecimal alreadyRefunded = nz(refundRepository.sumByInvoiceId(invoice.getId()));
            BigDecimal refundable = invoice.getAmountPaid().subtract(alreadyRefunded);
            if (amount.compareTo(refundable) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "مبلغ الاسترداد (" + amount + ") يتجاوز المدفوع على الفاتورة ناقص المسترد ("
                                + refundable + ")");
            }
        }

        // Money actually leaves through a bank account, so it is required rather than
        // defaulted: silently posting to cash on hand would misstate where it went.
        if (request.bankAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "يجب تحديد الحساب البنكي الذي تم الاسترداد منه");
        }
        BankAccount bank = bankAccountRepository.findByCompany_IdAndId(tenantId, request.bankAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "الحساب البنكي غير موجود"));

        Person client = invoice != null ? invoice.getClient() : resolveClient(request.clientId());
        if (!StringUtils.hasText(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "سبب الاسترداد مطلوب");
        }

        Refund refund = Refund.of(accounting.currentTenantReference());
        refund.setRefundNumber(accounting.nextNumber(AccountingSequence.Scope.REFUND, date));
        refund.setRefundDate(date);
        refund.setClient(client);
        refund.setInvoice(invoice);
        refund.setAmount(amount);
        refund.setCurrency("SAR");
        refund.setReason(request.reason());
        refund.setPaymentMethod(request.paymentMethod());
        refund.setBankAccount(bank);
        refund.setNotes(request.notes());
        refund.setCreatedBy(accounting.currentUser());

        String description = "استرداد من عميل";
        List<AccountingService.Line> lines = new ArrayList<>();
        lines.add(AccountingService.Line.dr(amount, bank.getAccount(), description, client, bank));
        if (invoice != null) {
            lines.add(AccountingService.Line.cr(amount,
                    accounting.requireAccount(AccountingService.ACC_AR), description, client, null));
        } else {
            lines.add(AccountingService.Line.cr(amount,
                    accounting.requireAccount(AccountingService.ACC_CLIENT_CREDIT), description, client, null));
        }

        JournalEntry entry = accounting.post(date, description, JournalEntry.Source.REFUND,
                "REFUND", null, accounting.currentUser(), lines);
        refund.setJournalEntry(entry);
        Refund saved = refundRepository.save(refund);
        entry.setReferenceId(saved.getId());

        if (invoice != null) {
            // Give the money back on the invoice so its status reflects the refund.
            invoice.setAmountPaid(invoice.getAmountPaid().subtract(amount));
            invoiceRepository.save(invoice);
        }

        auditLogService.recordSuccess("ACCOUNTING_REFUND_ISSUED", accounting.currentUser(),
                "Refund", saved.getId(),
                java.util.Map.of("refundNumber", saved.getRefundNumber(),
                        "amount", amount.toPlainString()));
        return RefundResponse.from(saved);
    }

    @Transactional
    public void voidRefund(UUID id) {
        Refund refund = load(id);
        if (refund.getVoidedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الاسترداد ملغى بالفعل");
        }
        accounting.reverse(refund.getJournalEntry(), accounting.currentUser());
        refund.setVoidedAt(Instant.now());
        refundRepository.save(refund);

        Invoice invoice = refund.getInvoice();
        if (invoice != null) {
            invoice.setAmountPaid(invoice.getAmountPaid().add(refund.getAmount()));
            invoiceRepository.save(invoice);
        }
        auditLogService.recordSuccess("ACCOUNTING_REFUND_VOIDED", accounting.currentUser(),
                "Refund", refund.getId(), java.util.Map.of());
    }

    private Refund load(UUID id) {
        return refundRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الاسترداد غير موجود"));
    }

    private Person resolveClient(UUID clientId) {
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "يجب تحديد الفاتورة أو العميل للاسترداد");
        }
        return personRepository.findScopedById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "العميل غير موجود"));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}