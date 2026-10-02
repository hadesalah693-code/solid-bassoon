package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.repository.AccountRepository;
import com.kalima.backend.accounting.repository.BankAccountRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Installs the system chart of accounts (see {@link SystemAccountCatalog}) for a
 * company, plus a default cash bank account so the ledger is usable immediately.
 *
 * <p>Why this exists: the original seed lived entirely inside
 * {@code V60__accounting_module.sql} as {@code INSERT ... SELECT ... FROM companies},
 * so it only ever provisioned companies that already existed when the migration ran.
 * Every company created afterwards started with an empty chart, and
 * {@link AccountingService#requireAccount} then failed on the very first invoice.
 *
 * <p>The seeder is <b>idempotent</b>: it inserts only the codes that are missing and
 * leaves existing rows untouched, so it is safe to call on every request path and it
 * also back-fills companies that were created before this class existed.
 */
@Service
public class ChartOfAccountsSeeder {

    private static final Logger log = LoggerFactory.getLogger(ChartOfAccountsSeeder.class);

    private final AccountRepository accountRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CompanyRepository companyRepository;
    private final String baseCurrency;

    /**
     * Companies already verified in this JVM. The posting path calls the seeder on
     * every account resolution, so the existence check is memoised; the
     * {@code uq_accounts_code} unique constraint still guards concurrent inserts.
     */
    private final Set<UUID> verified = ConcurrentHashMap.newKeySet();

    public ChartOfAccountsSeeder(AccountRepository accountRepository,
                                 BankAccountRepository bankAccountRepository,
                                 CompanyRepository companyRepository,
                                 @Value("${kalima.accounting.base-currency:SAR}") String baseCurrency) {
        this.accountRepository = accountRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.companyRepository = companyRepository;
        this.baseCurrency = baseCurrency;
    }

    /**
     * Ensures the chart of the company in the current tenant context. Called on the
     * posting path so a company can never be left without the accounts the ledger
     * engine depends on.
     *
     * @return number of accounts actually inserted (0 when already complete)
     */
    @Transactional
    public int ensureCurrentTenantSeeded() {
        UUID tenantId = TenantContext.getRequired();
        if (!verified.add(tenantId)) return 0;
        try {
            return ensureSeeded(tenantId);
        } catch (RuntimeException e) {
            verified.remove(tenantId);
            throw e;
        }
    }

    /**
     * Ensures the chart of an explicit company. Safe to call repeatedly and for
     * companies other than the current tenant (used by administrative back-fill).
     */
    @Transactional
    public int ensureSeeded(UUID companyId) {
        List<String> codes = SystemAccountCatalog.ALL.stream().map(SystemAccountCatalog.SystemAccount::code).toList();
        Set<String> existing = new HashSet<>();
        Map<String, Account> byCode = new HashMap<>();
        for (Account a : accountRepository.findByCompany_IdAndCodeIn(companyId, codes)) {
            existing.add(a.getCode());
            byCode.put(a.getCode(), a);
        }

        // The company must be resolved from the repository, never from an account row.
        // Deriving it via `findByCode("1000")` made the whole seeder unusable for the
        // exact case it exists to serve: a company with an empty chart has no account
        // to read the company from, so it threw instead of seeding itself.
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("الشركة غير موجودة: " + companyId));

        // Pass 1: rows themselves, so every parent has an id before children link to it.
        int inserted = 0;
        for (SystemAccountCatalog.SystemAccount def : SystemAccountCatalog.ALL) {
            if (existing.contains(def.code())) continue;
            Account account = new Account();
            account.setCompany(company);
            account.setCode(def.code());
            account.setNameAr(def.nameAr());
            account.setNameEn(def.nameEn());
            account.setType(def.type());
            account.setActive(true);
            account.setSystem(true);
            byCode.put(def.code(), accountRepository.saveAndFlush(account));
            inserted++;
        }

        if (inserted > 0) {
            linkHierarchy(company, byCode);
            log.info("Seeded {} system accounts for company {}", inserted, companyId);
        }

        ensureDefaultCashAccount(company, byCode);
        return inserted;
    }

    /**
     * Attaches each account to the group header of its own type. The V60 seed left
     * every parent null, which made the tree in the UI decorative; the codes below are
     * the header rows declared in {@link SystemAccountCatalog}.
     */
    private void linkHierarchy(Company company, Map<String, Account> byCode) {
        Map<Account.Type, String> header = Map.of(
                Account.Type.ASSET, "1000",
                Account.Type.LIABILITY, "2000",
                Account.Type.EQUITY, "3000",
                Account.Type.REVENUE, "4000",
                Account.Type.EXPENSE, "5000");
        boolean dirty = false;
        for (SystemAccountCatalog.SystemAccount def : SystemAccountCatalog.ALL) {
            Account account = byCode.get(def.code());
            if (account == null || account.getParent() != null) continue;
            String parentCode = header.get(def.type());
            if (parentCode == null || parentCode.equals(def.code())) continue;
            Account parent = byCode.get(parentCode);
            if (parent == null) continue;
            account.setParent(parent);
            dirty = true;
        }
        if (dirty) {
            accountRepository.saveAll(byCode.values());
            accountRepository.flush();
        }
    }

    /**
     * Guarantees at least one usable bank account. Expenses, receipts and payments all
     * need a cash/bank leg, and V60 only created one for the companies that existed at
     * migration time.
     */
    private void ensureDefaultCashAccount(Company company, Map<String, Account> byCode) {
        boolean any = bankAccountRepository.findByCompany_IdOrderByNameArAsc(company.getId()).stream()
                .findAny().isPresent();
        if (any) return;
        Account cash = byCode.get(AccountingService.ACC_CASH);
        if (cash == null) return;
        BankAccount bank = new BankAccount();
        bank.setCompany(company);
        bank.setAccount(cash);
        bank.setNameAr("الصندوق");
        bank.setCurrency(baseCurrency);
        bank.setOpeningBalance(BigDecimal.ZERO);
        bank.setActive(true);
        bankAccountRepository.saveAndFlush(bank);
        log.info("Created default cash bank account for company {}", company.getId());
    }
}
