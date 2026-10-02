package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {

    @Query("""
            select l.entry.entryDate, l.entry.entryNumber, l.entry.referenceType,
                   l.entry.referenceId, coalesce(l.description, l.entry.description), l.debit, l.credit
            from JournalLine l
            where l.company.id = :companyId and l.account.id = :accountId
              and l.entry.posted = true and l.entry.entryDate >= :from and l.entry.entryDate <= :to
            order by l.entry.entryDate desc, l.entry.entryNumber desc, l.lineNo desc
            """)
    Page<Object[]> accountLedgerRows(
            @Param("companyId") UUID companyId,
            @Param("accountId") UUID accountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l
            where l.company.id = :companyId and l.account.id = :accountId
              and l.entry.posted = true and l.entry.entryDate < :before
            """)
    BigDecimal accountBalanceBefore(
            @Param("companyId") UUID companyId,
            @Param("accountId") UUID accountId,
            @Param("before") LocalDate before);

    @Query("""
            select coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0)
            from JournalLine l
            where l.company.id = :companyId and l.account.id = :accountId
              and l.entry.posted = true and l.entry.entryDate >= :from and l.entry.entryDate <= :to
            """)
    Object[] accountPeriodTotals(
            @Param("companyId") UUID companyId,
            @Param("accountId") UUID accountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Client statement lines, in date order. Restricted to the receivable
     * account: the bank leg of a payment is also client-tagged and would
     * otherwise show up as a second, contradictory row. The AR account is
     * debit-positive, so debits increase what the client owes.
     */
    @Query("""
            select l.entry.entryDate, l.entry.entryNumber, l.entry.description,
                   l.description, l.debit, l.credit
            from JournalLine l
            where l.company.id = :companyId
              and l.client.id = :clientId
              and l.account.id = :accountId
              and l.entry.posted = true
              and l.entry.entryDate >= :from
              and l.entry.entryDate <= :to
            order by l.entry.entryDate asc, l.entry.entryNumber asc, l.lineNo asc
            """)
    List<Object[]> clientStatementRows(
            @Param("companyId") UUID companyId,
            @Param("clientId") UUID clientId,
            @Param("accountId") UUID accountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l
            where l.company.id = :companyId
              and l.client.id = :clientId
              and l.account.id = :accountId
              and l.entry.posted = true
              and l.entry.entryDate < :to
            """)
    BigDecimal clientBalanceBefore(
            @Param("companyId") UUID companyId,
            @Param("clientId") UUID clientId,
            @Param("accountId") UUID accountId,
            @Param("to") LocalDate to);

    /**
     * Net movement of one account type over a period, always as a positive magnitude:
     * revenue is credit minus debit, expenses debit minus credit. Reading by type
     * rather than by a hand-listed set of codes means a new account joins the
     * dashboard and the P&amp;L without any code change.
     */
    @Query("""
            select coalesce(sum(
                case when l.account.type = com.kalima.backend.accounting.entity.Account.Type.REVENUE
                     then l.credit - l.debit
                     else l.debit - l.credit end), 0)
            from JournalLine l
            where l.company.id = :companyId
              and l.entry.posted = true
              and l.entry.entryDate between :from and :to
              and l.account.type = :type
            """)
    BigDecimal movementByType(
            @Param("companyId") UUID companyId,
            @Param("type") com.kalima.backend.accounting.entity.Account.Type type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Current balance of one account from the ledger. */
    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l
            where l.company.id = :companyId
              and l.account.id = :accountId
              and l.entry.posted = true
            """)
    BigDecimal accountBalance(@Param("companyId") UUID companyId,
                              @Param("accountId") UUID accountId);

    /**
     * Actual spend for a budget period: net debit over expense accounts, optionally
     * narrowed to one account. Credits are subtracted so a refund or reversal
     * against an expense reduces the actual rather than being ignored.
     */
    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l
            where l.company.id = :companyId
              and l.entry.posted = true
              and l.entry.entryDate between :from and :to
              and l.account.type = com.kalima.backend.accounting.entity.Account.Type.EXPENSE
              and (:accountId is null or l.account.id = :accountId)
            """)
    BigDecimal budgetActual(@Param("companyId") UUID companyId,
                            @Param("accountId") UUID accountId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    /**
     * Net cost of a case over a period: every expense-account line tagged to the
     * case. Read straight from the ledger so it always agrees with the P&L.
     */
    @Query("""
            select coalesce(sum(l.debit - l.credit), 0)
            from JournalLine l
            where l.company.id = :companyId
              and l.caseFile.id = :caseId
              and l.entry.posted = true
              and l.entry.entryDate between :from and :to
              and l.account.type = com.kalima.backend.accounting.entity.Account.Type.EXPENSE
            """)
    BigDecimal caseCost(@Param("companyId") UUID companyId,
                        @Param("caseId") UUID caseId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to);

    /** Revenue recognised against a case, from fee/revenue accounts tagged to it. */
    @Query("""
            select coalesce(sum(l.credit - l.debit), 0)
            from JournalLine l
            where l.company.id = :companyId
              and l.caseFile.id = :caseId
              and l.entry.posted = true
              and l.entry.entryDate between :from and :to
              and l.account.type = com.kalima.backend.accounting.entity.Account.Type.REVENUE
            """)
    BigDecimal caseRevenue(@Param("companyId") UUID companyId,
                           @Param("caseId") UUID caseId,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    /**
     * Every case in the tenant that carries ledger movement in the period, with the
     * revenue and cost already aggregated, so the profitability report is one query
     * instead of a per-case loop.
     */
    @Query("""
            select l.caseFile.id,
                   sum(case when l.account.type = com.kalima.backend.accounting.entity.Account.Type.REVENUE
                            then l.credit - l.debit else 0 end),
                   sum(case when l.account.type = com.kalima.backend.accounting.entity.Account.Type.EXPENSE
                            then l.debit - l.credit else 0 end)
            from JournalLine l
            where l.company.id = :companyId
              and l.caseFile.id is not null
              and l.entry.posted = true
              and l.entry.entryDate between :from and :to
              and l.account.type in (com.kalima.backend.accounting.entity.Account.Type.REVENUE,
                                     com.kalima.backend.accounting.entity.Account.Type.EXPENSE)
            group by l.caseFile.id
            """)
    List<Object[]> caseProfitability(@Param("companyId") UUID companyId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);
}
