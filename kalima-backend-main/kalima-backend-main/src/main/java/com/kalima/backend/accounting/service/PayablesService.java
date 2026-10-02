package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.VendorBillCreateRequest;
import com.kalima.backend.accounting.dto.VendorBillResponse;
import com.kalima.backend.accounting.dto.VendorCreateRequest;
import com.kalima.backend.accounting.dto.VendorPaymentCreateRequest;
import com.kalima.backend.accounting.dto.VendorPaymentResponse;
import com.kalima.backend.accounting.dto.VendorResponse;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.AccountingSequence;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.entity.Vendor;
import com.kalima.backend.accounting.entity.VendorBill;
import com.kalima.backend.accounting.entity.VendorPayment;
import com.kalima.backend.accounting.repository.BankAccountRepository;
import com.kalima.backend.accounting.repository.VendorBillRepository;
import com.kalima.backend.accounting.repository.VendorPaymentRepository;
import com.kalima.backend.accounting.repository.VendorRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vendors and payables.
 *
 * <p>A bill recognises the expense and the liability together
 * ({@code Dr expense + Dr 2210 / Cr 2100}); paying it settles the liability
 * ({@code Dr 2100 / Cr bank}). VAT input is an asset, not an expense, so it sits on
 * its own debit line rather than being folded into the cost.
 */
@Service
public class PayablesService {

    private final VendorRepository vendorRepository;
    private final VendorBillRepository billRepository;
    private final VendorPaymentRepository paymentRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public PayablesService(VendorRepository vendorRepository,
                           VendorBillRepository billRepository,
                           VendorPaymentRepository paymentRepository,
                           BankAccountRepository bankAccountRepository,
                           AccountingService accounting,
                           AuditLogService auditLogService) {
        this.vendorRepository = vendorRepository;
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------
    // Vendors
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<VendorResponse> listVendors(boolean activeOnly, String term) {
        UUID tenantId = TenantContext.getRequired();
        return vendorRepository.search(tenantId, activeOnly,
                        StringUtils.hasText(term) ? term.trim() : null).stream()
                .map(v -> VendorResponse.from(v, outstanding(v)))
                .toList();
    }

    @Transactional(readOnly = true)
    public VendorResponse getVendor(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        Vendor vendor = loadVendor(id);
        return VendorResponse.from(vendor, outstanding(vendor));
    }

    @Transactional
    public VendorResponse createVendor(@Valid VendorCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        if (vendorRepository.findByCompany_IdAndName(tenantId, request.name().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "يوجد مورد مسجل بنفس الاسم");
        }
        Vendor vendor = Vendor.of(accounting.currentTenantReference());
        vendor.setName(request.name().trim());
        vendor.setNameAr(request.nameAr());
        vendor.setEmail(request.email());
        vendor.setPhone(request.phone());
        vendor.setTaxNumber(request.taxNumber());
        vendor.setAddress(request.address());
        vendor.setCurrency("SAR");
        if (request.expenseAccountId() != null) {
            Account expense = accounting.requireAccountById(request.expenseAccountId());
            vendor.setExpenseAccount(expense);
        }
        vendor.setActive(true);
        vendor.setNotes(request.notes());
        vendor.setCreatedBy(accounting.currentUser());
        Vendor saved = vendorRepository.save(vendor);

        auditLogService.recordSuccess("ACCOUNTING_VENDOR_CREATED", accounting.currentUser(),
                "Vendor", saved.getId(), java.util.Map.of("name", saved.getName()));
        return VendorResponse.from(saved, outstanding(saved));
    }

    @Transactional
    public VendorResponse updateVendor(UUID id, @Valid VendorCreateRequest request) {
        Vendor vendor = loadVendor(id);
        vendor.setNameAr(request.nameAr());
        vendor.setEmail(request.email());
        vendor.setPhone(request.phone());
        vendor.setTaxNumber(request.taxNumber());
        vendor.setAddress(request.address());
        if (request.expenseAccountId() != null) {
            vendor.setExpenseAccount(accounting.requireAccountById(request.expenseAccountId()));
        }
        vendor.setNotes(request.notes());
        Vendor saved = vendorRepository.save(vendor);
        return VendorResponse.from(saved, outstanding(saved));
    }

    /**
     * Deactivates rather than deletes: bills already reference this vendor, and
     * removing it would break the audit trail on historical payables.
     */
    @Transactional
    public void deactivateVendor(UUID id) {
        Vendor vendor = loadVendor(id);
        vendor.setActive(false);
        vendorRepository.save(vendor);
        auditLogService.recordSuccess("ACCOUNTING_VENDOR_DEACTIVATED", accounting.currentUser(),
                "Vendor", vendor.getId(), java.util.Map.of());
    }

    // ---------------------------------------------------------------
    // Bills
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<VendorBillResponse> listBills(UUID vendorId, VendorBill.Status status,
                                              LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusYears(1) : from;
        LocalDate today = LocalDate.now();
        return billRepository.search(TenantContext.getRequired(), vendorId, status, start, end)
                .stream().map(b -> VendorBillResponse.from(b, today)).toList();
    }

    @Transactional
    public VendorBillResponse createBill(@Valid VendorBillCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate date = request.billDate() == null ? LocalDate.now() : request.billDate();

        Vendor vendor = vendorRepository.findByCompany_IdAndId(tenantId, request.vendorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "المورد غير موجود"));
        if (!vendor.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن تسجيل فاتورة لمورد غير نشط");
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
        BigDecimal total = subtotal.add(taxAmount);

        if (request.dueDate() != null && request.dueDate().isBefore(date)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "تاريخ الاستحقاق لا يسبق تاريخ الفاتورة");
        }

        Account expenseAccount = accounting.requireAccountById(request.expenseAccountId());
        if (expenseAccount.getType() != Account.Type.EXPENSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "يجب أن يكون الحساب المحدد حساب مصروفات");
        }

        VendorBill bill = VendorBill.of(accounting.currentTenantReference());
        bill.setBillNumber(nextBillNumber(date));
        bill.setVendor(vendor);
        bill.setBillDate(date);
        bill.setDueDate(request.dueDate());
        bill.setExpenseAccount(expenseAccount);
        bill.setSubtotal(subtotal);
        bill.setTaxRate(taxRate);
        bill.setTaxAmount(taxAmount);
        bill.setTotal(total);
        bill.setAmountPaid(BigDecimal.ZERO);
        bill.setStatus(VendorBill.Status.OPEN);
        bill.setReference(request.reference());
        bill.setNotes(request.notes());
        bill.setCreatedBy(accounting.currentUser());

        String description = "فاتورة مورد: " + vendor.getName();
        List<AccountingService.Line> lines = new java.util.ArrayList<>();
        lines.add(AccountingService.Line.dr(subtotal, expenseAccount, description));
        if (taxAmount.signum() > 0) {
            lines.add(AccountingService.Line.dr(taxAmount,
                    accounting.requireAccount(AccountingService.ACC_VAT_INPUT), description));
        }
        lines.add(AccountingService.Line.cr(total,
                accounting.requireAccount(AccountingService.ACC_AP), description));

        JournalEntry entry = accounting.post(date, description,
                JournalEntry.Source.VENDOR_BILL, "VENDOR_BILL", null,
                accounting.currentUser(), lines);
        bill.setJournalEntry(entry);
        VendorBill saved;
        try {
            saved = billRepository.save(bill);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "رقم الفاتورة مستخدم بالفعل؛ أعد المحاولة");
        }
        entry.setReferenceId(saved.getId());

        auditLogService.recordSuccess("ACCOUNTING_VENDOR_BILL_CREATED", accounting.currentUser(),
                "VendorBill", saved.getId(),
                java.util.Map.of("billNumber", saved.getBillNumber(),
                        "total", total.toPlainString()));
        return VendorBillResponse.from(saved, LocalDate.now());
    }

    @Transactional
    public void cancelBill(UUID id) {
        VendorBill bill = loadBill(id);
        if (bill.getAmountPaid().signum() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن إلغاء فاتورة تم دفع جزء منها");
        }
        if (bill.getStatus() == VendorBill.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الفاتورة ملغاة بالفعل");
        }
        accounting.reverse(bill.getJournalEntry(), accounting.currentUser());
        bill.setStatus(VendorBill.Status.CANCELLED);
        bill.setVoidedAt(Instant.now());
        billRepository.save(bill);
        auditLogService.recordSuccess("ACCOUNTING_VENDOR_BILL_CANCELLED", accounting.currentUser(),
                "VendorBill", bill.getId(), java.util.Map.of());
    }

    // ---------------------------------------------------------------
    // Payments
    // ---------------------------------------------------------------
    @Transactional
    public VendorPaymentResponse payBill(@Valid VendorPaymentCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate date = request.paymentDate() == null ? LocalDate.now() : request.paymentDate();
        BigDecimal amount = request.amount();

        // Lock the bill before reading its remaining, so two concurrent payments
        // cannot both see the same balance and overpay it.
        VendorBill bill = billRepository.findForUpdateByCompanyIdAndId(tenantId, request.billId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الفاتورة غير موجودة"));
        if (bill.getStatus() == VendorBill.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الفاتورة ملغاة");
        }
        BigDecimal remaining = bill.remaining();
        if (amount.compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "مبلغ الدفعة (" + amount + ") يتجاوز المتبقي على الفاتورة (" + remaining + ")");
        }

        BankAccount bank = bankAccountRepository.findByCompany_IdAndId(tenantId, request.bankAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "الحساب البنكي غير موجود"));

        VendorPayment payment = VendorPayment.of(accounting.currentTenantReference());
        payment.setBill(bill);
        payment.setPaymentDate(date);
        payment.setAmount(amount);
        payment.setMethod(request.method());
        payment.setBankAccount(bank);
        payment.setReference(request.reference());
        payment.setNotes(request.notes());
        payment.setCreatedBy(accounting.currentUser());

        String description = "سداد فاتورة مورد: "
                + (bill.getVendor() == null ? "" : bill.getVendor().getName());
        List<AccountingService.Line> lines = List.of(
                AccountingService.Line.dr(amount,
                        accounting.requireAccount(AccountingService.ACC_AP), description),
                AccountingService.Line.cr(amount, bank.getAccount(), description, null, bank));

        JournalEntry entry = accounting.post(date, description, JournalEntry.Source.VENDOR_PAYMENT,
                "VENDOR_PAYMENT", null, accounting.currentUser(), lines);
        payment.setJournalEntry(entry);
        VendorPayment saved = paymentRepository.save(payment);
        entry.setReferenceId(saved.getId());

        BigDecimal paid = bill.getAmountPaid().add(amount);
        bill.setAmountPaid(paid);
        bill.setStatus(paid.compareTo(bill.getTotal()) >= 0
                ? VendorBill.Status.PAID
                : VendorBill.Status.PARTIALLY_PAID);
        billRepository.save(bill);

        auditLogService.recordSuccess("ACCOUNTING_VENDOR_PAYMENT_POSTED", accounting.currentUser(),
                "VendorPayment", saved.getId(),
                java.util.Map.of("amount", amount.toPlainString(),
                        "billNumber", bill.getBillNumber()));
        return VendorPaymentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<VendorPaymentResponse> listPayments(UUID billId, LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusYears(1) : from;
        if (billId != null) {
            return paymentRepository.findByCompanyIdAndBillId(tenantId, billId)
                    .stream().map(VendorPaymentResponse::from).toList();
        }
        return paymentRepository.search(tenantId, start, end)
                .stream().map(VendorPaymentResponse::from).toList();
    }

    @Transactional
    public void voidPayment(UUID id) {
        VendorPayment payment = paymentRepository
                .findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الدفعة غير موجودة"));
        if (payment.getVoidedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الدفعة ملغاة بالفعل");
        }
        VendorBill bill = payment.getBill();
        if (bill.getAmountPaid().compareTo(payment.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن إلغاء دفعة تتجاوز المدفوع على الفاتورة");
        }
        accounting.reverse(payment.getJournalEntry(), accounting.currentUser());
        payment.setVoidedAt(Instant.now());
        paymentRepository.save(payment);

        BigDecimal paid = bill.getAmountPaid().subtract(payment.getAmount());
        bill.setAmountPaid(paid);
        bill.setStatus(paid.signum() <= 0 ? VendorBill.Status.OPEN
                : paid.compareTo(bill.getTotal()) >= 0
                        ? VendorBill.Status.PAID
                        : VendorBill.Status.PARTIALLY_PAID);
        billRepository.save(bill);

        auditLogService.recordSuccess("ACCOUNTING_VENDOR_PAYMENT_VOIDED", accounting.currentUser(),
                "VendorPayment", payment.getId(), java.util.Map.of());
    }

    /** Payables ageing summary for the dashboard. */
    @Transactional(readOnly = true)
    public PayablesSummary summary(LocalDate on) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate today = on == null ? LocalDate.now() : on;
        return new PayablesSummary(
                nz(billRepository.sumOutstanding(tenantId)),
                billRepository.countOverdue(tenantId, today));
    }

    public record PayablesSummary(BigDecimal outstandingPayable, long overdueBills) {
    }

    private String nextBillNumber(LocalDate date) {
        return accounting.nextNumber(AccountingSequence.Scope.VENDOR_BILL, date);
    }

    private Vendor loadVendor(UUID id) {
        return vendorRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "المورد غير موجود"));
    }

    private VendorBill loadBill(UUID id) {
        return billRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الفاتورة غير موجودة"));
    }

    /** What this vendor is still owed, from the open and part-paid bills only. */
    private BigDecimal outstanding(Vendor vendor) {
        return nz(billRepository.sumOutstandingByVendor(
                TenantContext.getRequired(), vendor.getId()));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}