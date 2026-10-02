package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.BankAccountCreateRequest;
import com.kalima.backend.accounting.dto.BankAccountResponse;
import com.kalima.backend.accounting.dto.BankTransferRequest;
import com.kalima.backend.accounting.dto.BankTransferResponse;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.BankTransfer;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.repository.BankAccountRepository;
import com.kalima.backend.accounting.repository.BankTransferRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final BankTransferRepository transferRepository;
    private final AuditLogService auditLogService;
    private final AccountingService accounting;

    public BankAccountService(BankAccountRepository bankAccountRepository,
                              BankTransferRepository transferRepository,
                              AuditLogService auditLogService,
                              AccountingService accounting) {
        this.bankAccountRepository = bankAccountRepository;
        this.transferRepository = transferRepository;
        this.auditLogService = auditLogService;
        this.accounting = accounting;
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> list() {
        UUID tenantId = TenantContext.getRequired();
        return bankAccountRepository.findByCompany_IdOrderByNameArAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BankAccountResponse get(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        return toResponse(bankAccountRepository.findByCompany_IdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الحساب البنكي غير موجود")));
    }

    @Transactional
    public BankAccountResponse create(BankAccountCreateRequest request) {
        Account account;
        if (request.accountId() != null) {
            account = accounting.requireAccountById(request.accountId());
        } else {
            account = accounting.requireAccount(AccountingService.ACC_BANK);
        }
        requireAssetAccount(account);
        requireUnlinkedAccount(account.getId(), null);
        requireNoColumnOpeningBalance(request);

        BankAccount bank = new BankAccount();
        bank.setCompany(accounting.currentTenantReference());
        bank.setAccount(account);
        bank.setNameAr(request.nameAr().trim());
        if (StringUtils.hasText(request.bankName())) bank.setBankName(request.bankName().trim());
        if (StringUtils.hasText(request.iban())) bank.setIban(request.iban().trim());
        if (StringUtils.hasText(request.accountNumber())) bank.setAccountNumber(request.accountNumber().trim());
        bank.setCurrency(accounting.requireBaseCurrency(request.currency()));
        bank.setOpeningBalance(BigDecimal.ZERO);
        bank.setActive(true);
        return toResponse(bankAccountRepository.save(bank));
    }

    /**
     * The {@code opening_balance} column is no longer a balance source. Accepting a
     * value here would show a figure on this screen that the trial balance and balance
     * sheet never see, so the caller is pointed at the opening-balance workflow, which
     * posts a real balanced entry instead.
     */
    private void requireNoColumnOpeningBalance(BankAccountCreateRequest request) {
        BigDecimal requested = request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance();
        if (requested.signum() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "الرصيد الافتتاحي يُسجَّل الآن عبر قيد افتتاحي متوازن:"
                            + " POST /api/accounting/opening-balances، وليس كقيمة في الحساب البنكي");
        }
    }

    @Transactional
    public BankAccountResponse update(UUID id, BankAccountCreateRequest request) {
        BankAccount bank = bankAccountRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الحساب البنكي غير موجود"));
        requireNoColumnOpeningBalance(request);

        if (request.accountId() != null && !request.accountId().equals(bank.getAccount().getId())) {
            // Re-pointing an account that already carries movements would rewrite the
            // history of every past posting, so it is refused outright.
            if (bankAccountRepository.sumLedgerBalance(TenantContext.getRequired(), bank.getAccount().getId())
                    .signum() != 0
                    || bankAccountRepository.sumMovement(TenantContext.getRequired(), id,
                    AccountingDates.MIN, AccountingDates.MAX).signum() != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "لا يمكن تغيير الحساب المحاسبي لحساب بنكي عليه حركات؛"
                                + " أنشئ حساباً بنكياً جديداً أو سجّل قيد تسوية");
            }
            Account account = accounting.requireAccountById(request.accountId());
            requireAssetAccount(account);
            requireUnlinkedAccount(account.getId(), id);
            bank.setAccount(account);
        }
        bank.setNameAr(request.nameAr().trim());
        if (StringUtils.hasText(request.bankName())) bank.setBankName(request.bankName().trim());
        if (StringUtils.hasText(request.iban())) bank.setIban(request.iban().trim());
        if (StringUtils.hasText(request.accountNumber())) bank.setAccountNumber(request.accountNumber().trim());
        if (StringUtils.hasText(request.currency())) bank.setCurrency(accounting.requireBaseCurrency(request.currency()));
        return toResponse(bankAccountRepository.save(bank));
    }

    private void requireAssetAccount(Account account) {
        if (account.getType() != Account.Type.ASSET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "الحساب البنكي يجب أن يكون من نوع أصل (أصول)");
        }
    }

    private void requireUnlinkedAccount(UUID accountId, UUID selfId) {
        bankAccountRepository.findByCompany_IdAndAccount_Id(TenantContext.getRequired(), accountId)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "هذا الحساب مرتبط بحساب بنكي آخر: " + existing.getNameAr());
                });
    }

    @Transactional
    public BankAccountResponse setActive(UUID id, boolean active) {
        BankAccount bank = bankAccountRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الحساب البنكي غير موجود"));
        bank.setActive(active);
        bankAccountRepository.save(bank);
        return toResponse(bank);
    }

        @Transactional(readOnly = true)
    public List<BankTransferResponse> transfers() {
        UUID companyId = TenantContext.getRequired();
        return transferRepository.findByCompany(companyId).stream()
                .map(this::toTransferResponse)
                .toList();
    }

    @Transactional
    public BankTransferResponse transfer(BankTransferRequest request) {
        UUID companyId = TenantContext.getRequired();
        if (request.sourceBankAccountId().equals(request.destinationBankAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن التحويل إلى الحساب البنكي نفسه");
        }
        BankAccount source = bankAccountRepository.findByCompany_IdAndId(companyId, request.sourceBankAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الحساب المصدر غير موجود"));
        BankAccount destination = bankAccountRepository.findByCompany_IdAndId(companyId, request.destinationBankAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الحساب الوجهة غير موجود"));
        if (!source.isActive() || !destination.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحسابان البنكيان يجب أن يكونا نشطين");
        }
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "التحويل بين عملتين مختلفتين غير مدعوم دون قيد فروق عملة");
        }
        String currency = accounting.requireBaseCurrency(source.getCurrency());
        BigDecimal amount = request.amount().setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "مبلغ التحويل يجب أن يكون أكبر من صفر");
        }
        accounting.requireAccountById(source.getAccount().getId());
        accounting.requireAccountById(destination.getAccount().getId());
        String description = "تحويل داخلي من " + source.getNameAr() + " إلى " + destination.getNameAr();

        BankTransfer transfer = new BankTransfer();
        transfer.setCompany(accounting.currentTenantReference());
        transfer.setSourceBankAccount(source);
        transfer.setDestinationBankAccount(destination);
        transfer.setAmount(amount);
        transfer.setCurrency(currency);
        transfer.setTransferDate(request.transferDate());
        transfer.setReference(request.reference());
        transfer.setNotes(request.notes());
        transfer.setCreatedBy(accounting.currentUser());
        BankTransfer saved = transferRepository.saveAndFlush(transfer);

        // Internal movement between two of the firm's own bank accounts: debit the
        // destination ledger account, credit the source one. Total debit equals total
        // credit, so this cannot change the firm's total cash, and both legs carry the
        // bank account so bank-statement matching stays possible.
        JournalEntry entry = accounting.post(request.transferDate(), description,
                JournalEntry.Source.BANK_TRANSFER, "BANK_TRANSFER", saved.getId(),
                accounting.currentUser(), List.of(
                        AccountingService.Line.dr(amount, destination.getAccount(), description, null, destination),
                        AccountingService.Line.cr(amount, source.getAccount(), description, null, source)));
        saved.setJournalEntry(entry);
        transferRepository.save(saved);
        auditLogService.recordSuccess("BANK_TRANSFER_POSTED", accounting.currentUser(),
                "BankTransfer", saved.getId(), java.util.Map.of(
                        "source", source.getId().toString(),
                        "destination", destination.getId().toString(),
                        "amount", amount.toPlainString()));
        return toTransferResponse(saved);
    }

    private BankTransferResponse toTransferResponse(BankTransfer transfer) {
        return new BankTransferResponse(transfer.getId(),
                transfer.getSourceBankAccount().getId(), transfer.getSourceBankAccount().getNameAr(),
                transfer.getDestinationBankAccount().getId(), transfer.getDestinationBankAccount().getNameAr(),
                transfer.getAmount(), transfer.getCurrency(), transfer.getTransferDate(),
                transfer.getReference(), transfer.getNotes(),
                transfer.getJournalEntry() == null ? null : transfer.getJournalEntry().getId(),
                transfer.getJournalEntry() == null ? null : transfer.getJournalEntry().getEntryNumber());
    }

    private BankAccountResponse toResponse(BankAccount b) {
        UUID tenantId = TenantContext.getRequired();
        // Balance is taken from the ledger account, not from the legacy
        // `opening_balance` column, so this screen and the trial balance always agree.
        BigDecimal balance = nz(bankAccountRepository.sumLedgerBalance(tenantId, b.getAccount().getId()));
        BigDecimal opening = nz(bankAccountRepository.sumLedgerBalanceBySource(
                tenantId, b.getAccount().getId(), com.kalima.backend.accounting.entity.JournalEntry.Source.OPENING));
        return new BankAccountResponse(
                b.getId(), b.getNameAr(), b.getBankName(), b.getIban(), b.getAccountNumber(), b.getCurrency(),
                b.getAccount().getId(), b.getAccount().getCode(), opening,
                balance.subtract(opening), balance, b.isActive());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
