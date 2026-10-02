package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.RetainerApplicationCreateRequest;
import com.kalima.backend.accounting.dto.RetainerApplicationResponse;
import com.kalima.backend.accounting.dto.RetainerCreateRequest;
import com.kalima.backend.accounting.dto.RetainerResponse;
import com.kalima.backend.accounting.entity.*;
import com.kalima.backend.accounting.repository.*;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.invoice.entity.Invoice;
import com.kalima.backend.invoice.entity.InvoicePayment;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import com.kalima.backend.invoice.repository.InvoicePaymentRepository;
import com.kalima.backend.invoice.repository.InvoiceRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Client retainer (advance) management.
 * <p>
 * Flow:
 * 1. Client pays money in advance → {@code createRetainer} posts a journal entry:
 *    Dr 1100 Cash / Dr 1110 Bank   Cr 2300 Client Retainers (liability)
 * 2. When work is done, retainer is applied to an invoice/expense →
 *    {@code applyRetainer} posts a journal entry:
 *    Dr 2300 Client Retainers   Cr 4000 Revenue (or Dr 5000 Expense)
 * 3. Retainer status updates automatically based on applied amount.
 */
@Service
@Transactional
public class RetainerService {

    private final ClientRetainerRepository retainerRepository;
    private final RetainerApplicationRepository applicationRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final ExpenseRepository expenseRepository;
    private final BankAccountRepository bankAccountRepository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public RetainerService(ClientRetainerRepository retainerRepository,
                           RetainerApplicationRepository applicationRepository,
                           AccountRepository accountRepository,
                           JournalEntryRepository journalEntryRepository,
                           InvoiceRepository invoiceRepository,
                           InvoicePaymentRepository invoicePaymentRepository,
                           ExpenseRepository expenseRepository,
                           BankAccountRepository bankAccountRepository,
                           PersonRepository personRepository,
                           CompanyRepository companyRepository,
                           AccountingService accounting,
                           AuditLogService auditLogService) {
        this.retainerRepository = retainerRepository;
        this.applicationRepository = applicationRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
        this.expenseRepository = expenseRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    /** Create a new retainer and post the receipt journal entry. */
    public RetainerResponse createRetainer(RetainerCreateRequest req) {
        UUID tenantId = TenantContext.getRequired();

        Person client = personRepository.findScopedById(req.clientId())
                .orElseThrow(() -> new IllegalArgumentException("العميل غير موجود: " + req.clientId()));
        if (client.getSystemRole() != DomainEnums.SystemRole.CLIENT) {
            throw new IllegalArgumentException("الشخص المحدد ليس عميلاً");
        }
        Person receivedBy = accounting.currentUser();

        Company company = companyRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("الشركة غير موجودة: " + tenantId));

        // Ensure retainer liability account exists (code 2300)
        Account retainerAccount = accounting.requireAccount(AccountingService.ACC_RETAINER_LIABILITY);

        // Determine cash/bank account for the receipt
        BankAccount cashBank = resolveCashAccount(company);
        Account cashAccount = cashBank.getAccount();

        // Create retainer entity
        ClientRetainer retainer = new ClientRetainer();
        retainer.setCompanyId(tenantId);
        retainer.setClientId(client.getId());
        retainer.setAmount(req.amount());
        retainer.setCurrency(accounting.requireBaseCurrency(req.currency()));
        retainer.setReceivedDate(req.receivedDate());
        retainer.setReceivedBy(receivedBy.getId());
        retainer.setReference(req.reference());
        retainer.setNotes(req.notes());
        retainer = retainerRepository.saveAndFlush(retainer);

        // Post journal entry: Dr Cash/Bank  Cr 2300 Client Retainers
        AccountingService.Line dr = AccountingService.Line.dr(
            req.amount(), cashAccount, "سلفة عميل: " + client.getFullName(), null, cashBank);
        AccountingService.Line cr = AccountingService.Line.cr(
                req.amount(), retainerAccount, "سلفة عميل: " + client.getFullName(), client, null);

        JournalEntry receiptEntry = accounting.post(req.receivedDate(), "استلام سلفة عميل - " + client.getFullName(),
            JournalEntry.Source.RECEIPT, "RETAINER_RECEIPT", retainer.getId(), receivedBy,
            List.of(dr, cr));
        auditLogService.recordSuccess("ACCOUNTING_RETAINER_RECEIVED", receivedBy,
            "ClientRetainer", retainer.getId(), java.util.Map.of(
                "clientId", client.getId().toString(),
                "amount", req.amount().toPlainString(),
                "journalEntryId", receiptEntry.getId().toString()));

        return toResponse(retainer, BigDecimal.ZERO);
    }

    /** List retainers for current tenant. */
    @Transactional(readOnly = true)
    public List<RetainerResponse> listRetainers(UUID clientId, ClientRetainer.RetainerStatus status) {
        UUID tenantId = TenantContext.getRequired();
        List<ClientRetainer> retainers;
        if (clientId != null && status != null) {
            retainers = retainerRepository.findByCompanyIdAndClientIdOrderByReceivedDateDesc(tenantId, clientId)
                    .stream().filter(r -> r.getStatus() == status).toList();
        } else if (clientId != null) {
            retainers = retainerRepository.findByCompanyIdAndClientIdOrderByReceivedDateDesc(tenantId, clientId);
        } else if (status != null) {
            retainers = retainerRepository.findByCompanyIdAndStatusOrderByReceivedDateDesc(tenantId, status);
        } else {
            retainers = retainerRepository.findByCompanyIdOrderByReceivedDateDesc(tenantId);
        }
        return retainers.stream().map(r -> toResponseWithBalance(tenantId, r)).toList();
    }

    @Transactional(readOnly = true)
    public List<ClientRetainer> findAllByCompanyId(UUID companyId) {
        return retainerRepository.findByCompanyIdOrderByReceivedDateDesc(companyId);
    }

    /** Apply retainer to an invoice/expense/bill. */
    public RetainerApplicationResponse applyRetainer(RetainerApplicationCreateRequest req) {
        UUID tenantId = TenantContext.getRequired();

        ClientRetainer retainer = retainerRepository.findForUpdateByIdAndCompanyId(req.retainerId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("السلفة غير موجودة: " + req.retainerId()));

        if (retainer.getStatus() == ClientRetainer.RetainerStatus.CANCELLED) {
            throw new IllegalStateException("لا يمكن تطبيق سلفة ملغية");
        }

        // Check available balance
        BigDecimal appliedSoFar = applicationRepository.sumAppliedByRetainer(tenantId, req.retainerId());
        BigDecimal remaining = retainer.getAmount().subtract(appliedSoFar);
        if (req.appliedAmount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                    "المبلغ المطبق (" + req.appliedAmount() + ") يتجاوز المتبقي (" + remaining + ")");
        }

        if (req.targetType() != RetainerApplication.TargetType.INVOICE) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "تطبيق السلفة متاح على الفواتير فقط؛ أدرج المصروف القابل للتحميل ضمن فاتورة العميل");
        }
        Invoice invoice = invoiceRepository.findForUpdateByCompanyIdAndId(tenantId, req.targetId())
            .orElseThrow(() -> new IllegalArgumentException("الفاتورة غير موجودة: " + req.targetId()));
        if (!invoice.getClient().getId().equals(retainer.getClientId())) {
            throw new IllegalArgumentException("الفاتورة لا تخص عميل السلفة");
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("لا يمكن تطبيق السلفة على فاتورة مسودة أو ملغاة");
        }
        if (req.appliedAmount().compareTo(invoice.remaining()) > 0) {
            throw new IllegalArgumentException("المبلغ يتجاوز الرصيد المتبقي على الفاتورة");
        }

        Person actor = accounting.currentUser();
        RetainerApplication app = new RetainerApplication();
        app.setCompanyId(tenantId);
        app.setRetainerId(retainer.getId());
        app.setAppliedAmount(req.appliedAmount());
        app.setAppliedDate(req.appliedDate());
        app.setAppliedBy(actor.getId());
        app.setTargetType(req.targetType());
        app.setTargetId(req.targetId());
        app.setNotes(req.notes());
        app = applicationRepository.saveAndFlush(app);

        Account retainerAccount = accounting.requireAccount(AccountingService.ACC_RETAINER_LIABILITY);
        Account arAccount = accounting.requireAccount(AccountingService.ACC_AR);
        String targetRef = invoice.getInvoiceNumber();
        String description = "تطبيق سلفة " + retainer.getReference() + " على فاتورة " + targetRef;
        JournalEntry je = accounting.post(req.appliedDate(), description,
            JournalEntry.Source.PAYMENT, "RETAINER_APPLICATION", app.getId(), actor,
            List.of(
                AccountingService.Line.dr(req.appliedAmount(), retainerAccount, description,
                    invoice.getClient(), null),
                AccountingService.Line.cr(req.appliedAmount(), arAccount, description,
                    invoice.getClient(), null)));
        app.setJournalEntryId(je.getId());
        applicationRepository.saveAndFlush(app);

        InvoicePayment payment = new InvoicePayment();
        payment.setCompany(companyRepository.getReferenceById(tenantId));
        payment.setInvoice(invoice);
        payment.setAmount(req.appliedAmount());
        payment.setPaymentDate(req.appliedDate());
        payment.setMethod("RETAINER");
        payment.setReference(retainer.getReference());
        payment.setNotes(req.notes());
        payment.setJournalEntry(je);
        payment.setRecordedBy(actor);
        invoicePaymentRepository.save(payment);
        invoice.setAmountPaid(invoice.getAmountPaid().add(req.appliedAmount()));
        invoice.setStatus(invoice.getAmountPaid().compareTo(invoice.getTotal()) >= 0
            ? InvoiceStatus.PAID
            : invoice.getDueDate() != null && invoice.getDueDate().isBefore(req.appliedDate())
                ? InvoiceStatus.OVERDUE : InvoiceStatus.PARTIALLY_PAID);
        invoiceRepository.save(invoice);

        // Update retainer status
        BigDecimal newApplied = appliedSoFar.add(req.appliedAmount());
        if (newApplied.compareTo(retainer.getAmount()) == 0) {
            retainer.setStatus(ClientRetainer.RetainerStatus.FULLY_APPLIED);
        }
        retainerRepository.saveAndFlush(retainer);
        auditLogService.recordSuccess("ACCOUNTING_RETAINER_APPLIED", actor,
            "RetainerApplication", app.getId(), java.util.Map.of(
                "retainerId", retainer.getId().toString(),
                "invoiceId", invoice.getId().toString(),
                "amount", req.appliedAmount().toPlainString(),
                "journalEntryId", je.getId().toString()));

        return toApplicationResponse(app, targetRef);
    }

    private BankAccount resolveCashAccount(Company company) {
        // Prefer default cash bank account (1100) else first active bank account
        return bankAccountRepository.findByCompany_IdOrderByNameArAsc(company.getId())
                .stream()
                .filter(BankAccount::isActive)
                .filter(b -> b.getAccount().getCode().equals(AccountingService.ACC_CASH))
                .findFirst()
                .orElseGet(() -> bankAccountRepository.findByCompany_IdOrderByNameArAsc(company.getId())
                        .stream()
                    .filter(BankAccount::isActive)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("لا يوجد حساب نقدي/بنكي للشركة")));
    }

    // --- mapping ---

    private RetainerResponse toResponse(ClientRetainer r, BigDecimal applied) {
        Person client = personRepository.findScopedById(r.getClientId()).orElse(null);
        Person receivedBy = personRepository.findScopedById(r.getReceivedBy()).orElse(null);
        return new RetainerResponse(
            r.getId(), r.getCompanyId(), r.getClientId(), client == null ? null : client.getFullName(),
                r.getAmount(), r.getCurrency(),
                RetainerResponse.RetainerStatus.valueOf(r.getStatus().name()),
            r.getReceivedDate(), r.getReceivedBy(), receivedBy == null ? null : receivedBy.getFullName(),
                r.getReference(), r.getNotes(),
                applied, r.getAmount().subtract(applied),
                r.getCreatedAt().toString()
        );
    }

    private RetainerResponse toResponseWithBalance(UUID tenantId, ClientRetainer r) {
        BigDecimal applied = applicationRepository.sumAppliedByRetainer(tenantId, r.getId());
        return toResponse(r, applied);
    }

    private RetainerApplicationResponse toApplicationResponse(RetainerApplication a, String targetRef) {
        Person appliedBy = personRepository.findScopedById(a.getAppliedBy()).orElse(null);
        return new RetainerApplicationResponse(
                a.getId(), a.getCompanyId(), a.getRetainerId(), a.getAppliedAmount(),
                a.getAppliedDate(), a.getAppliedBy(), appliedBy == null ? null : appliedBy.getFullName(),
                a.getTargetType(),
                a.getTargetId(), targetRef, a.getJournalEntryId(), a.getNotes(),
                a.getCreatedAt().toString()
        );
    }

    @Transactional(readOnly = true)
    public List<RetainerApplicationResponse> listApplications(UUID retainerId) {
        UUID tenantId = TenantContext.getRequired();
        retainerRepository.findByIdAndCompanyId(retainerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("السلفة غير موجودة: " + retainerId));
        return applicationRepository.findByCompanyIdAndRetainerIdOrderByAppliedDateAsc(tenantId, retainerId)
                .stream()
                .map(app -> toApplicationResponse(app, applicationTargetReference(app, tenantId)))
                .toList();
    }

    private String applicationTargetReference(RetainerApplication application, UUID tenantId) {
        if (application.getTargetType() == RetainerApplication.TargetType.INVOICE) {
            return invoiceRepository.findByCompany_IdAndId(tenantId, application.getTargetId())
                    .map(Invoice::getInvoiceNumber).orElse(null);
        }
        if (application.getTargetType() == RetainerApplication.TargetType.EXPENSE) {
            return expenseRepository.findByCompany_IdAndId(tenantId, application.getTargetId())
                    .map(Expense::getExpenseNumber).orElse(null);
        }
        return null;
    }
}