package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.BankReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, UUID> {

    Optional<BankReconciliation> findByCompany_IdAndId(UUID companyId, UUID id);

    Optional<BankReconciliation> findByCompany_IdAndBankAccount_IdAndStatementDate(
            UUID companyId, UUID bankAccountId, LocalDate statementDate);

    @Query("""
            SELECT r FROM BankReconciliation r
            WHERE r.company.id = :companyId AND r.bankAccount.id = :bankAccountId
            ORDER BY r.statementDate DESC
            """)
    List<BankReconciliation> findByAccount(@Param("companyId") UUID companyId,
                                           @Param("bankAccountId") UUID bankAccountId);

    @Query("""
            select count(r) from BankReconciliation r
            where r.company.id = :companyId
              and r.status = com.kalima.backend.accounting.entity.BankReconciliation.Status.IN_PROGRESS
            """)
    int countInProgress(@Param("companyId") UUID companyId);

    /** Newest completed reconciliation, whose balance is the starting point for the next. */
    @Query("""
            SELECT r FROM BankReconciliation r
            WHERE r.company.id = :companyId AND r.bankAccount.id = :bankAccountId
              AND r.status = com.kalima.backend.accounting.entity.BankReconciliation.Status.COMPLETED
              AND r.statementDate <= :on
            ORDER BY r.statementDate DESC
            """)
    List<BankReconciliation> findLastCompleted(@Param("companyId") UUID companyId,
                                               @Param("bankAccountId") UUID bankAccountId,
                                               @Param("on") LocalDate on);
}