package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.AllocationLineRequest;
import com.kalima.backend.accounting.dto.ReceiptCreateRequest;
import com.kalima.backend.accounting.dto.ReceiptResponse;
import com.kalima.backend.accounting.entity.AccountingSequence;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.entity.Receipt;
import com.kalima.backend.accounting.entity.ReceiptAllocation;
import com.kalima.backend.accounting.repository.ReceiptAllocationRepository;
import com.kalima.backend.accounting.repository.ReceiptRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.tenant.TenantContext;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptAllocationRepository allocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final AuditLogService auditLogService;
    private final AccountingService accounting;

    public ReceiptService(ReceiptRepository receiptRepository,
                          ReceiptAllocationRepository allocationRepository,
                          InvoiceRepository invoiceRepository,
                          InvoicePaymentRepository invoicePaymentRepository,
                          AuditLogService auditLogService,
                          AccountingService accounting) {
        this.receiptRepository = receiptRepository;
        this.allocationRepository = allocationRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
        this.auditLogService = auditLogService;
        this.accounting = accounting;
    }

    @Transactional(readOnly = true)
    public Page<ReceiptResponse> list(UUID clientId, LocalDate from, LocalDate to,
                                      String search, Pageable pageable) {
        String term = StringUtils.hasText(search) ? search.trim() : null;
        return receiptRepository.search(TenantContext.getRequired(), clientId,
                        AccountingDates.from(from), AccountingDates.to(to), term, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(UUID id) {
        return receiptRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "المقبوض غير موجود"));
    }

    @Transactional
    public ReceiptResponse create(ReceiptCreateRequest request) {
        BigDecimal amount = request.amount();
        if (amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "المبلغ يجب أن يكون أكبر من صفر");
        }
        BankAccount bank = accounting.findBankAccount(request.bankAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحساب البنكي غير موجود"));
        if (!bank.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحساب البنكي موقوف");
        }

        List<AllocationLineRequest> requested = normaliseRequestedAllocations(request);
        boolean deposit = Boolean.TRUE.equals(request.deposit());

        // The client can be inferred from a single invoice, but a multi-invoice receipt
        // must name the client explicitly: money from two different clients must never
        // settle into one on-account balance.
        Person client = request.clientId() != null ? accounting.requirePerson(request.clientId()) : null;
        if (client == null && requested.size() == 1) {
            client = accounting.requirePerson(resolveInvoiceClient(requested.get(0).invoiceId()));
        }
        if (deposit && !requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن تخصيص سند وديعة على فواتير؛ الوديعة التزام على العميل");
        }
        if (deposit && client == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "يجب تحديد العميل لتسجيل أموال عميل كوديعة/أمانة");
        }
        if (client != null && !deposit && requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "خصص قبض العميل على فاتورة، أو سجله كوديعة عميل");
        }

        BigDecimal allocatedTotal = requested.stream()
                .map(AllocationLineRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedTotal.compareTo(amount) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "إجمالي المبلغ المخصص (" + allocatedTotal + ") يتجاوز مبلغ السند (" + amount + ")");
        }

        // Lock every invoice before posting, so two concurrent receipts cannot both
        // pass the remaining-balance check and overpay the same invoice.
        List<Invoice> invoices = new ArrayList<>();
        for (AllocationLineRequest line : requested) {
            Invoice invoice = invoiceRepository.findForUpdateByCompanyIdAndId(
                            TenantContext.getRequired(), line.invoiceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الفاتورة غير موجودة"));
            if (client == null || !invoice.getClient().getId().equals(client.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "الفاتورة المحددة لا تطابق العميل أو نوع السند");
            }
            if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "لا يمكن تخصيص سند قبض لفاتورة مسودة أو ملغاة");
            }
            if (line.amount().compareTo(invoice.remaining()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "مبلغ التخصيص يتجاوز الرصيد المتبقي على الفاتورة " + invoice.getInvoiceNumber());
            }
            invoices.add(invoice);
        }

        Receipt receipt = new Receipt();
        receipt.setCompany(accounting.currentTenantReference());
        receipt.setReceiptNumber(accounting.nextNumber(AccountingSequence.Scope.RECEIPT, request.receiptDate()));
        receipt.setReceiptDate(request.receiptDate());
        receipt.setClient(client);
        // The legacy single-invoice column is kept in sync for the one-invoice case
        // only; receipt_allocations is the source of truth for everything else.
        receipt.setInvoice(invoices.size() == 1 ? invoices.get(0) : null);
        receipt.setBankAccount(bank);
        receipt.setAmount(amount);
        receipt.setCurrency(accounting.requireBaseCurrency(request.currency()));
        receipt.setDeposit(deposit);
        if (StringUtils.hasText(request.paymentMethod())) receipt.setPaymentMethod(request.paymentMethod().trim());
        if (StringUtils.hasText(request.reference())) receipt.setReference(request.reference().trim());
        if (StringUtils.hasText(request.notes())) receipt.setNotes(request.notes().trim());
        receipt.setRecordedBy(accounting.currentUser());

        Receipt saved = receiptRepository.saveAndFlush(receipt);

        // Client advances are liabilities, not office revenue. Allocated client money
        // relieves AR line by line; whatever is left stays on the unapplied client
        // credit account so it can be allocated later without re-collecting it. Only
        // non-client general receipts post income. The client tag is attached to the
        // AR and client-credit legs only: tagging a revenue line with a client would
        // pollute the client's statement with a credit that is not a debt.
        BigDecimal unapplied = amount.subtract(allocatedTotal);
        String description = client != null
                ? "تحصيل من العميل " + client.getFullName()
                : "إيداع نقدي";

        List<AccountingService.Line> lines = new ArrayList<>();
        lines.add(AccountingService.Line.dr(amount, bank.getAccount(), description, null, bank));
        for (int i = 0; i < invoices.size(); i++) {
            lines.add(AccountingService.Line.cr(requested.get(i).amount(),
                    accounting.requireAccount(AccountingService.ACC_AR),
                    "تحصيل " + invoices.get(i).getInvoiceNumber(), invoices.get(i).getClient(), null));
        }
        if (deposit) {
            lines.add(AccountingService.Line.cr(amount,
                    accounting.requireAccount(AccountingService.ACC_RETAINER_LIABILITY),
                    description, client, null));
        } else if (unapplied.signum() > 0) {
            lines.add(AccountingService.Line.cr(unapplied,
                    accounting.requireAccount(AccountingService.ACC_CLIENT_CREDIT),
                    "دفعة مقدمة غير مخصصة من العميل " + client.getFullName(), client, null));
        } else if (invoices.isEmpty()) {
            lines.add(AccountingService.Line.cr(amount,
                    accounting.requireAccount(AccountingService.ACC_OTHER_INCOME), description, null, null));
        }

        JournalEntry entry = accounting.post(request.receiptDate(),
                saved.getReceiptNumber() + " — " + description,
                JournalEntry.Source.RECEIPT, "RECEIPT", saved.getId(), accounting.currentUser(), lines);
        saved.setJournalEntry(entry);

        for (int i = 0; i < invoices.size(); i++) {
            recordAllocation(saved, entry, invoices.get(i), requested.get(i).amount(),
                    request.receiptDate(), request.notes());
        }
        return toResponse(receiptRepository.save(saved));
    }

    /**
     * Normalises both entry points into one list: an explicit multi-invoice
     * {@code allocations} array, or the legacy single {@code invoiceId} shortcut.
     */
    private List<AllocationLineRequest> normaliseRequestedAllocations(ReceiptCreateRequest request) {
        if (request.allocations() != null && !request.allocations().isEmpty()) {
            return request.allocations();
        }
        if (request.invoiceId() != null) {
            return List.of(new AllocationLineRequest(request.invoiceId(), request.amount()));
        }
        return List.of();
    }

    private UUID resolveInvoiceClient(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByCompany_IdAndId(TenantContext.getRequired(), invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الفاتورة غير موجودة"));
        return invoice.getClient().getId();
    }

    /**
     * Persists the three rows that must agree for an allocation to be real: the
     * allocation itself, the invoice payment that mirrors it in the invoice ledger,
     * and the invoice's own amount_paid/status. The caller has already posted the
     * journal, so {@code entry} is shared rather than posted twice.
     */
    private void recordAllocation(Receipt receipt, JournalEntry entry, Invoice invoice,
                                  BigDecimal lineAmount, LocalDate date, String notes) {
        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.setCompany(accounting.currentTenantReference());
        allocation.setReceipt(receipt);
        allocation.setInvoice(invoice);
        allocation.setAmount(lineAmount);
        allocation.setAllocationDate(date);
        allocation.setAllocatedBy(accounting.currentUser());
        if (StringUtils.hasText(notes)) allocation.setNotes(notes.trim());
        allocationRepository.saveAndFlush(allocation);

        invoice.setAmountPaid(invoice.getAmountPaid().add(lineAmount));
        invoice.setStatus(deriveInvoiceStatus(invoice, date));
        invoiceRepository.save(invoice);

        InvoicePayment payment = new InvoicePayment();
        payment.setCompany(accounting.currentTenantReference());
        payment.setInvoice(invoice);
        payment.setReceipt(receipt);
        payment.setAmount(lineAmount);
        payment.setPaymentDate(date);
        payment.setMethod(receipt.getPaymentMethod());
        payment.setReference(receipt.getReference());
        payment.setNotes(notes);
        payment.setJournalEntry(entry);
        payment.setRecordedBy(accounting.currentUser());
        invoicePaymentRepository.save(payment);
    }

    @Transactional
    public void delete(UUID id) {
        Receipt receipt = receiptRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "المقبوض غير موجود"));
        if (receipt.getJournalEntry() != null
                && receipt.getJournalEntry().getReversedEntry() == null) {
            accounting.reverse(receipt.getJournalEntry(), accounting.currentUser());
        }
        // The receipt journal is already reversed, so the invoice side only has to be
        // unwound to match: drop the allocations' effect on amount_paid and void the
        // mirrored invoice payments. The legacy receipts.invoice_id shortcut is not
        // touched here: V74 backfilled every such receipt into receipt_allocations, so
        // decrementing through it as well would undo the payment twice.
        List<ReceiptAllocation> allocations = allocationRepository
                .findByCompanyIdAndReceiptId(TenantContext.getRequired(), id);
        for (ReceiptAllocation allocation : allocations) {
            if (allocation.isVoided()) continue;
            Invoice invoice = allocation.getInvoice();
            invoice.setAmountPaid(invoice.getAmountPaid().subtract(allocation.getAmount()).max(BigDecimal.ZERO));
            invoice.setStatus(deriveInvoiceStatus(invoice, LocalDate.now()));
            invoiceRepository.save(invoice);
            allocation.setVoidedAt(Instant.now());
        }
        allocationRepository.saveAll(allocations);

        List<InvoicePayment> payments = invoicePaymentRepository.findByCompany_IdAndReceipt_Id(
                TenantContext.getRequired(), receipt.getId());
        payments.forEach(payment -> payment.setVoidedAt(Instant.now()));
        invoicePaymentRepository.saveAll(payments);

        receipt.setVoidedAt(Instant.now());
        receiptRepository.save(receipt);
        auditLogService.recordSuccess("ACCOUNTING_RECEIPT_VOIDED", accounting.currentUser(),
                "Receipt", receipt.getId(), java.util.Map.of(
                        "receiptNumber", receipt.getReceiptNumber(),
                        "journalEntryId", receipt.getJournalEntry() == null ? "" : receipt.getJournalEntry().getId().toString()));
    }

    private static InvoiceStatus deriveInvoiceStatus(Invoice invoice, LocalDate paymentDate) {
        if (invoice.getAmountPaid().compareTo(invoice.getTotal()) >= 0) return InvoiceStatus.PAID;
        if (invoice.getAmountPaid().signum() > 0) {
            return invoice.getDueDate() != null && invoice.getDueDate().isBefore(paymentDate)
                    ? InvoiceStatus.OVERDUE : InvoiceStatus.PARTIALLY_PAID;
        }
        return invoice.getDueDate() != null && invoice.getDueDate().isBefore(paymentDate)
                ? InvoiceStatus.OVERDUE : InvoiceStatus.ISSUED;
    }

    private ReceiptResponse toResponse(Receipt r) {
        return new ReceiptResponse(
                r.getId(), r.getReceiptNumber(), r.getReceiptDate(),
                r.getClient() == null ? null : r.getClient().getId(),
                r.getClient() == null ? null : r.getClient().getFullName(),
                r.getInvoice() == null ? null : r.getInvoice().getId(),
                r.getBankAccount() == null ? null : r.getBankAccount().getId(),
                r.getBankAccount() == null ? null : r.getBankAccount().getNameAr(),
                r.getAmount(), r.getCurrency(), r.getPaymentMethod(), r.getReference(),
                r.isDeposit(),
                r.getJournalEntry() == null ? null : r.getJournalEntry().getId(),
                r.getJournalEntry() == null ? null : r.getJournalEntry().getEntryNumber(),
                r.getNotes(),
                r.getRecordedBy() == null ? null : r.getRecordedBy().getFullName(),
                r.getCreatedAt() == null ? null : LocalDate.ofInstant(r.getCreatedAt(),
                        java.time.ZoneId.systemDefault()));
    }
}