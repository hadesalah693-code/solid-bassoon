package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.BankAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    @EntityGraph(attributePaths = "account")
    List<BankAccount> findByCompany_IdOrderByNameArAsc(UUID companyId);

    @EntityGraph(attributePaths = "account")
    Optional<BankAccount> findByCompany_IdAndId(UUID companyId, UUID id);

    /** One cash account must not back two bank accounts, or movement is counted twice. */
    Optional<BankAccount> findByCompany_IdAndAccount_Id(UUID companyId, UUID accountId);

    /**
     * Balance = opening + (debits - credits) posted against the bank account's
     * ledger account. ASSET accounts are debit-positive.
     */
    @Query("""
            select coalesce(b.openingBalance, 0) + coalesce((
                select sum(l.debit - l.credit)
                from JournalLine l, JournalEntry e
                where l.entry = e
                  and e.company.id = b.company.id
                  and e.posted = true
                  and l.account.id = b.account.id
            ), 0)
            from BankAccount b
            where b.company.id = :companyId
            """)
    List<Object[]> computeBalances(@Param("companyId") UUID companyId);

    /**
     * Balance of the bank account's ledger account, all time, debit-positive.
     *
     * <p>This is the only balance used by the API. The previous split — a
     * {@code bank_accounts.opening_balance} column plus movement counted by
     * {@code bank_account_id} tag — produced a figure that could never agree with the
     * trial balance, because the opening column was never posted to the ledger.
     */
    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and l.account.id = :accountId
            """)
    java.math.BigDecimal sumLedgerBalance(
            @Param("companyId") UUID companyId,
            @Param("accountId") UUID accountId);

    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and e.entryDate <= :asOf
              and l.account.id = :accountId
            """)
    java.math.BigDecimal sumLedgerBalanceAsOf(
            @Param("companyId") UUID companyId,
            @Param("accountId") UUID accountId,
            @Param("asOf") java.time.LocalDate asOf);

    /**
     * Portion of an account's balance contributed by entries of one source. Used to
     * report the opening balance as a ledger fact instead of a stored column.
     */
    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and l.account.id = :accountId
              and e.source = :source
            """)
    java.math.BigDecimal sumLedgerBalanceBySource(
            @Param("companyId") UUID companyId,
            @Param("accountId") UUID accountId,
            @Param("source") com.kalima.backend.accounting.entity.JournalEntry.Source source);

    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and e.entryDate <= :asOf
              and e.source = :source
              and l.account.id = :accountId
            """)
    java.math.BigDecimal sumLedgerBalanceBySourceAsOf(
            @Param("companyId") UUID companyId,
            @Param("accountId") UUID accountId,
            @Param("source") com.kalima.backend.accounting.entity.JournalEntry.Source source,
            @Param("asOf") java.time.LocalDate asOf);

    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and l.bankAccount.id = :bankAccountId
              and e.entryDate >= :from
              and e.entryDate <= :to
            """)
    java.math.BigDecimal sumMovement(
            @Param("companyId") UUID companyId,
            @Param("bankAccountId") UUID bankAccountId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);
}
