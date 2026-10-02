package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.BankStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, UUID> {

    Optional<BankStatementLine> findByCompany_IdAndId(UUID companyId, UUID id);

    @Query("""
            SELECT l FROM BankStatementLine l
            WHERE l.company.id = :companyId
              AND l.bankAccount.id = :bankAccountId
              AND l.lineDate BETWEEN :from AND :to
            ORDER BY l.lineDate, l.id
            """)
    List<BankStatementLine> findByAccountInPeriod(@Param("companyId") UUID companyId,
                                                   @Param("bankAccountId") UUID bankAccountId,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    @Query("""
            SELECT l FROM BankStatementLine l
            WHERE l.company.id = :companyId
              AND l.bankAccount.id = :bankAccountId
              AND (:onlyUnmatched = FALSE OR l.matchedId IS NULL)
              AND l.lineDate <= :to
            ORDER BY l.lineDate, l.id
            """)
    List<BankStatementLine> findForReconciliation(@Param("companyId") UUID companyId,
                                                   @Param("bankAccountId") UUID bankAccountId,
                                                   @Param("to") LocalDate to,
                                                   @Param("onlyUnmatched") boolean onlyUnmatched);

    /** Candidate receipts for a statement line: same account, same day, same amount. */
    @Query("""
            SELECT r FROM Receipt r
            WHERE r.company.id = :companyId
              AND r.bankAccount.id = :bankAccountId
              AND r.receiptDate = :on
              AND r.amount = :amount
            """)
    List<com.kalima.backend.accounting.entity.Receipt> suggestReceipts(
            @Param("companyId") UUID companyId,
            @Param("bankAccountId") UUID bankAccountId,
            @Param("on") LocalDate on,
            @Param("amount") BigDecimal amount);
}