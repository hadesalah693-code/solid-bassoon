package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @EntityGraph(attributePaths = "parent")
    List<Account> findByCompany_IdOrderByCodeAsc(UUID companyId);

    List<Account> findByCompany_IdAndTypeOrderByCodeAsc(UUID companyId, Account.Type type);

    @EntityGraph(attributePaths = "parent")
    Optional<Account> findByCompany_IdAndId(UUID companyId, UUID id);

    @EntityGraph(attributePaths = "parent")
    Optional<Account> findByCompany_IdAndCode(UUID companyId, String code);

    boolean existsByCompany_IdAndCode(UUID companyId, String code);

    /**
     * Bulk lookup used by the chart-of-accounts seeder. This must return a list:
     * an {@code ...In} query matches many rows, so the previous
     * {@code Optional<Account>} signature was only ever a latent bug.
     */
    @EntityGraph(attributePaths = "parent")
    List<Account> findByCompany_IdAndCodeIn(UUID companyId, List<String> codes);

    /** Debit-positive balance for ASSET/EXPENSE accounts. */
    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and l.account.id in :accountIds
              and e.entryDate >= :from
              and e.entryDate <= :to
            """)
    BigDecimal sumDebitMinusCredit(
            @Param("companyId") UUID companyId,
            @Param("accountIds") List<UUID> accountIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select l.account.id, coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and e.entryDate >= :from
              and e.entryDate <= :to
            group by l.account.id
            """)
    List<Object[]> sumBalancesByAccount(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select count(l)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and l.account.id = :accountId
            """)
    long countMovements(@Param("companyId") UUID companyId,
                        @Param("accountId") UUID accountId);

    @Query("""
            select coalesce(sum(l.debit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and l.account.id in :accountIds
              and e.entryDate >= :from
              and e.entryDate <= :to
            """)
    BigDecimal sumDebit(
            @Param("companyId") UUID companyId,
            @Param("accountIds") List<UUID> accountIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(l.credit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and l.account.id in :accountIds
              and e.entryDate >= :from
              and e.entryDate <= :to
            """)
    BigDecimal sumCredit(
            @Param("companyId") UUID companyId,
            @Param("accountIds") List<UUID> accountIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select l.account.id, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0)
            from JournalLine l, JournalEntry e
            where l.entry = e
              and e.company.id = :companyId
              and e.posted = true
              and l.account.id in :accountIds
              and e.entryDate >= :from
              and e.entryDate <= :to
            group by l.account.id
            """)
    List<Object[]> debitCreditByAccount(
            @Param("companyId") UUID companyId,
            @Param("accountIds") List<UUID> accountIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
