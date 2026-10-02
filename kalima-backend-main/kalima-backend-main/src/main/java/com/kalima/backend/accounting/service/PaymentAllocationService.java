package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.AllocationCreateRequest;
import com.kalima.backend.accounting.dto.AllocationLineRequest;
import com.kalima.backend.accounting.dto.AllocationResponse;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.entity.Receipt;
import com.kalima.backend.accounting.entity.ReceiptAllocation;
import com.kalima.backend.accounting.repository.ReceiptAllocationRepository;
import com.kalima.backend.accounting.repository.ReceiptRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.invoice.entity.Invoice;
import com.kalima.backend.invoice.entity.InvoicePayment;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import com.kalima.backend.invoice.repository.InvoicePaymentRepository;
import com.kalima.backend.invoice.repository.InvoiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Applies already-collected money to invoices, and takes it back off again.
 *
 * <p>Two shapes are supported and both end up in {@code receipt_allocations}:
 * money allocated when the receipt is issued ({@link ReceiptService}) and money
 * allocated later against a receipt that was booked as unapplied client credit.
 * A late allocation moves the credit from 2350 to AR; unapplying reverses exactly
 * that movement, so the client's balance returns to unapplied credit instead of
 * forcing a refund.
 */
@Service
public class PaymentAllocationService {

    private final ReceiptAllocationRepository allocationRepository;
    private final ReceiptRepository receiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public PaymentAllocationService(ReceiptAllocationRepository allocationRepository,
                                    ReceiptRepository receiptRepository,
                                    InvoiceRepository invoiceRepository,
                                    InvoicePaymentRepository invoicePaymentRepository,
                                    AccountingService accounting,
                                    AuditLogService auditLogService) {
        this.allocationRepository = allocationRepository;
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> listByReceipt(UUID receiptId) {
        return allocationRepository.findByCompanyIdAndReceiptId(TenantContext.getRequired(), receiptId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> listByInvoice(UUID invoiceId) {
        return allocationRepository.findByCompanyIdAndInvoiceId(TenantContext.getRequired(), invoiceId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Allocates part of an already-issued receipt to one or more invoices.
     *
     * <p>Only money that is still parked on the unapplied client credit account can
     * move: a receipt issued against invoices had its full amount credited straight
     * to AR, so re-allocating it would double-relieve the receivable.
     */
    @Transactional
    public List<AllocationResponse> allocate(AllocationCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        Receipt receipt = receiptRepository.findByCompany_IdAndId(tenantId, request.receiptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "المقبوض غير موجود"));

        LocalDate date = request.allocationDate() == null ? receipt.getReceiptDate() : request.allocationDate();

        BigDecimal alreadyAllocated = allocationRepository.sumLiveByReceiptId(receipt.getId());
        BigDecimal unapplied = receipt.getAmount().subtract(alreadyAllocated);
        if (unapplied.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "سند القبض مخصص بالكامل بالفعل؛ استخدم إلغاء التخصيص قبل إعادة التخصيص");
        }
        if (request.total().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "يجب أن يكون إجمالي المبلغ المخصص أكبر من صفر");
        }
        if (request.total().compareTo(unapplied) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "إجمالي المبلغ المخصص (" + request.total() + ") يتجاوز الرصيد غير المخصص على السند ("
                            + unapplied + ")");
        }
        if (receipt.getClient() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن تخصيص سند غير مسجل باسم عميل على فواتير");
        }
        if (receipt.isDeposit()) {
            // A retainer deposit is credited to the retainer liability, never to
            // unapplied client credit, so there is no 2350 balance to move and
            // reliving AR would invent a payment the client never made.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "سند القبض عهدة (دفعة مقدمة) ولا يمكن تخصيصه على فواتير؛ "
                            + "يمكن تطبيقه على ملف أو خصمه من فاتورة قائمة");
        }

        // Lock every target invoice first: locking one at a time lets two concurrent
        // allocations settle against the same invoice and overpay it.
        List<Invoice> invoices = new java.util.ArrayList<>();
        for (AllocationLineRequest line : request.allocations()) {
            if (line.amount() == null || line.amount().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "كل مبلغ تخصيص يجب أن يكون أكبر من صفر");
            }
            Invoice invoice = invoiceRepository.findForUpdateByCompanyIdAndId(tenantId, line.invoiceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الفاتورة غير موجودة"));
            if (!invoice.getClient().getId().equals(receipt.getClient().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "الفاتورة لا تخص عميل سند القبض");
            }
            if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "لا يمكن تخصيص دفعة على فاتورة مسودة أو ملغاة");
            }
            if (line.amount().compareTo(invoice.remaining()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "مبلغ التخصيص يتجاوز الرصيد المتبقي على الفاتورة " + invoice.getInvoiceNumber());
            }
            invoices.add(invoice);
        }

        Account ar = accounting.requireAccount(AccountingService.ACC_AR);
        Account clientCredit = accounting.requireAccount(AccountingService.ACC_CLIENT_CREDIT);
        List<AccountingService.Line> lines = new java.util.ArrayList<>();
        for (int i = 0; i < invoices.size(); i++) {
            lines.add(AccountingService.Line.dr(request.allocations().get(i).amount(), clientCredit,
                    "تخصيص دفعة لفاتورة " + invoices.get(i).getInvoiceNumber(),
                    receipt.getClient(), null));
        }
        for (int i = 0; i < invoices.size(); i++) {
            lines.add(AccountingService.Line.cr(request.allocations().get(i).amount(), ar,
                    "تحصيل " + invoices.get(i).getInvoiceNumber(), invoices.get(i).getClient(), null));
        }

        String description = "تخصيص سند قبض " + receipt.getReceiptNumber()
                + " (" + request.total().toPlainString() + ")";
        JournalEntry entry = accounting.post(date, description,
                JournalEntry.Source.PAYMENT, "RECEIPT_ALLOCATION", receipt.getId(),
                accounting.currentUser(), lines);

        for (int i = 0; i < invoices.size(); i++) {
            writeAllocation(receipt, entry, invoices.get(i), request.allocations().get(i).amount(),
                    date, request.notes());
        }

        auditLogService.recordSuccess("ACCOUNTING_ALLOCATION_CREATED", accounting.currentUser(),
                "ReceiptAllocation", receipt.getId(), java.util.Map.of(
                        "receiptNumber", receipt.getReceiptNumber(),
                        "total", request.total().toPlainString(),
                        "invoices", invoices.size(),
                        "journalEntryId", entry.getId().toString()));

        return allocationRepository.findByCompanyIdAndReceiptId(tenantId, receipt.getId())
                .stream().map(this::toResponse).toList();
    }

    /**
     * Unapplies a single allocation and reverses only its AR relief.
     *
     * <p>The receipt keeps its cash; the money goes back to the unapplied client
     * credit account, which is where it came from. Reversing the receipt itself is
     * still available but is a different, whole-document operation.
     */
    @Transactional
    public AllocationResponse unallocate(UUID allocationId) {
        UUID tenantId = TenantContext.getRequired();
        ReceiptAllocation allocation = allocationRepository
                .findByCompany_IdAndId(tenantId, allocationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "التخصيص غير موجود"));
        if (allocation.isVoided()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "التخصيص ملغى مسبقاً");
        }

        Invoice invoice = invoiceRepository.findForUpdateByCompanyIdAndId(tenantId, allocation.getInvoice().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الفاتورة غير موجودة"));
        Receipt receipt = allocation.getReceipt();

        Account ar = accounting.requireAccount(AccountingService.ACC_AR);
        Account clientCredit = accounting.requireAccount(AccountingService.ACC_CLIENT_CREDIT);
        String description = "إلغاء تخصيص سند قبض " + receipt.getReceiptNumber()
                + " عن فاتورة " + invoice.getInvoiceNumber();

        JournalEntry reversal = accounting.post(allocation.getAllocationDate(), description,
                JournalEntry.Source.PAYMENT, "RECEIPT_ALLOCATION_REVERSAL", allocation.getId(),
                accounting.currentUser(), List.of(
                        AccountingService.Line.dr(allocation.getAmount(), ar, description,
                                invoice.getClient(), null),
                        AccountingService.Line.cr(allocation.getAmount(), clientCredit, description,
                                receipt.getClient(), null)));

        allocation.setVoidedAt(Instant.now());
        allocation.setReversalJournalEntry(reversal);
        allocationRepository.save(allocation);

        invoice.setAmountPaid(invoice.getAmountPaid().subtract(allocation.getAmount()).max(BigDecimal.ZERO));
        invoice.setStatus(deriveInvoiceStatus(invoice, allocation.getAllocationDate()));
        invoiceRepository.save(invoice);

        // The mirrored invoice payment must be voided too, otherwise the invoice
        // payment history still claims money that is no longer applied.
        invoicePaymentRepository.findByCompany_IdAndReceipt_Id(tenantId, receipt.getId()).stream()
                .filter(payment -> payment.getInvoice().getId().equals(invoice.getId())
                        && payment.getVoidedAt() == null
                        && payment.getAmount().compareTo(allocation.getAmount()) == 0)
                .forEach(payment -> {
                    payment.setVoidedAt(Instant.now());
                    invoicePaymentRepository.save(payment);
                });

        auditLogService.recordSuccess("ACCOUNTING_ALLOCATION_VOIDED", accounting.currentUser(),
                "ReceiptAllocation", allocation.getId(), java.util.Map.of(
                        "receiptNumber", receipt.getReceiptNumber(),
                        "invoiceNumber", invoice.getInvoiceNumber(),
                        "amount", allocation.getAmount().toPlainString(),
                        "reversalJournalEntryId", reversal.getId().toString()));

        return toResponse(allocation);
    }

    private void writeAllocation(Receipt receipt, JournalEntry entry, Invoice invoice,
                                 BigDecimal amount, LocalDate date, String notes) {
        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.setCompany(accounting.currentTenantReference());
        allocation.setReceipt(receipt);
        allocation.setInvoice(invoice);
        allocation.setAmount(amount);
        allocation.setAllocationDate(date);
        allocation.setAllocatedBy(accounting.currentUser());
        if (StringUtils.hasText(notes)) allocation.setNotes(notes.trim());
        allocationRepository.saveAndFlush(allocation);

        invoice.setAmountPaid(invoice.getAmountPaid().add(amount));
        invoice.setStatus(deriveInvoiceStatus(invoice, date));
        invoiceRepository.save(invoice);

        InvoicePayment payment = new InvoicePayment();
        payment.setCompany(accounting.currentTenantReference());
        payment.setInvoice(invoice);
        payment.setReceipt(receipt);
        payment.setAmount(amount);
        payment.setPaymentDate(date);
        payment.setMethod(receipt.getPaymentMethod());
        payment.setReference(receipt.getReference());
        payment.setNotes(notes);
        payment.setJournalEntry(entry);
        payment.setRecordedBy(accounting.currentUser());
        invoicePaymentRepository.save(payment);
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

    private AllocationResponse toResponse(ReceiptAllocation a) {
        Invoice invoice = a.getInvoice();
        Receipt receipt = a.getReceipt();
        return new AllocationResponse(
                a.getId(), receipt.getId(), receipt.getReceiptNumber(),
                invoice.getId(), invoice.getInvoiceNumber(),
                invoice.getClient() == null ? null : invoice.getClient().getId(),
                invoice.getClient() == null ? null : invoice.getClient().getFullName(),
                a.getAmount(), a.getAllocationDate(),
                invoice.getTotal(), invoice.getAmountPaid(), invoice.remaining(),
                a.isVoided(),
                a.getVoidedAt() == null ? null : LocalDate.ofInstant(a.getVoidedAt(),
                        java.time.ZoneId.systemDefault()),
                a.getReversalJournalEntry() == null ? null : a.getReversalJournalEntry().getId(),
                a.getNotes());
    }
}