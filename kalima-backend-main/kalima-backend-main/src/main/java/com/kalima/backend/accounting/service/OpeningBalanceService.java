package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.OpeningBalanceRequest;
import com.kalima.backend.accounting.dto.OpeningBalanceResponse;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.entity.JournalLine;
import com.kalima.backend.accounting.repository.BankAccountRepository;
import com.kalima.backend.accounting.repository.JournalEntryRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Posts a firm's opening balances as a real {@code OPENING} journal entry.
 *
 * <p>This exists because {@code bank_accounts.opening_balance} and
 * {@code client_accounts.opening_balance} were columns outside the ledger: a figure
 * typed into them appeared on the bank screen yet never appeared in the trial balance
 * or the balance sheet. Every balance now has exactly one source of truth — the
 * ledger — so the opening position is simply the first balanced entry.
 *
 * <p>Amounts are signed: a positive amount debits the account, a negative amount
 * credits it. Assets therefore take positive amounts, liabilities and equity negative
 * ones, and the entry is rejected unless the two sides are equal.
 */
@Service
public class OpeningBalanceService {

    private final AccountingService accounting;
    private final JournalEntryRepository journalEntryRepository;
    private final BankAccountRepository bankAccountRepository;

    public OpeningBalanceService(AccountingService accounting,
                                 JournalEntryRepository journalEntryRepository,
                                 BankAccountRepository bankAccountRepository) {
        this.accounting = accounting;
        this.journalEntryRepository = journalEntryRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasOpeningEntry() {
        return journalEntryRepository.existsByCompany_IdAndSource(
                TenantContext.getRequired(), JournalEntry.Source.OPENING);
    }

    @Transactional
    public OpeningBalanceResponse post(OpeningBalanceRequest request) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate effectiveDate = LocalDate.parse(request.effectiveDate());

        if (hasOpeningEntry()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "تم تسجيل الأرصدة الافتتاحية مسبقاً؛ التصحيح يتم بقيد عكسي أو قيد تسوية،"
                            + " ولا يمكن تكرار قيد الافتتاح");
        }

        // Merge duplicate accounts first: the same account listed twice would otherwise
        // produce two lines that the line-level constraint and the UI both find odd.
        Map<UUID, BigDecimal> netByAccount = new LinkedHashMap<>();
        for (OpeningBalanceRequest.Line line : request.lines()) {
            Account account = accounting.requireAccountById(line.accountId());
            if (account.getType() == Account.Type.REVENUE || account.getType() == Account.Type.EXPENSE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "لا يمكن فتح رصيد لحساب أرباح أو مصروفات (" + account.getCode() + ")");
            }
            netByAccount.merge(account.getId(), line.amount(), BigDecimal::add);
        }

        List<AccountingService.Line> lines = new ArrayList<>();
        Map<UUID, BankAccount> bankTags = bankTagsByAccount(tenantId);
        for (Map.Entry<UUID, BigDecimal> entry : netByAccount.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount.signum() == 0) continue;
            Account account = accounting.requireAccountById(entry.getKey());
            BankAccount bank = bankTags.get(entry.getKey());
            String description = "رصيد افتتاحي " + account.getCode();
            if (amount.signum() > 0) {
                lines.add(AccountingService.Line.dr(amount, account, description, null, bank));
            } else {
                lines.add(AccountingService.Line.cr(amount.abs(), account, description, null, bank));
            }
        }
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "الأرصدة الافتتاحية لا يمكن أن تكون كلها صفراً");
        }

        JournalEntry entry = accounting.post(effectiveDate,
                "الأرصدة الافتتاحية" + (request.notes() == null || request.notes().isBlank()
                        ? "" : " — " + request.notes().trim()),
                JournalEntry.Source.OPENING, "OPENING_BALANCE", null, accounting.currentUser(), lines);

        List<OpeningBalanceResponse.AccountBalance> balances = new ArrayList<>();
        for (JournalLine line : entry.getLines()) {
            balances.add(new OpeningBalanceResponse.AccountBalance(
                    line.getAccount().getId(),
                    line.getAccount().getCode(),
                    line.getAccount().getNameAr(),
                    line.getDebit().subtract(line.getCredit())));
        }
        return new OpeningBalanceResponse(
                entry.getId(),
                entry.getEntryNumber(),
                effectiveDate,
                entry.getTotalDebit(),
                entry.getTotalCredit(),
                balances,
                entry.getCreatedBy() == null ? null : entry.getCreatedBy().getFullName());
    }

    /**
     * Cash and bank lines are tagged with their bank account so the opening figure is
     * also visible in per-bank movement reports without a second source of truth.
     */
    private Map<UUID, BankAccount> bankTagsByAccount(UUID tenantId) {
        Map<UUID, BankAccount> tags = new LinkedHashMap<>();
        for (BankAccount bank : bankAccountRepository.findByCompany_IdOrderByNameArAsc(tenantId)) {
            tags.putIfAbsent(bank.getAccount().getId(), bank);
        }
        return tags;
    }
}
