package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.VendorPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorPaymentRepository extends JpaRepository<VendorPayment, UUID> {

    Optional<VendorPayment> findByCompany_IdAndId(UUID companyId, UUID id);

    @Query("""
            SELECT p FROM VendorPayment p
            WHERE p.company.id = :companyId AND p.bill.id = :billId
            ORDER BY p.paymentDate DESC
            """)
    List<VendorPayment> findByCompanyIdAndBillId(@Param("companyId") UUID companyId,
                                                  @Param("billId") UUID billId);

    @Query("""
            SELECT p FROM VendorPayment p
            WHERE p.company.id = :companyId
              AND p.paymentDate BETWEEN :from AND :to
            ORDER BY p.paymentDate DESC
            """)
    List<VendorPayment> search(@Param("companyId") UUID companyId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /**
     * Every payment that has already used a given bank account on a given date,
     * used by reconciliation to suggest matches for a statement line.
     */
    @Query("""
            SELECT p FROM VendorPayment p
            WHERE p.company.id = :companyId
              AND p.bankAccount.id = :bankAccountId
              AND p.paymentDate = :on
              AND p.amount = :amount
            """)
    List<VendorPayment> suggestForLine(@Param("companyId") UUID companyId,
                                       @Param("bankAccountId") UUID bankAccountId,
                                       @Param("on") LocalDate on,
                                       @Param("amount") java.math.BigDecimal amount);
}