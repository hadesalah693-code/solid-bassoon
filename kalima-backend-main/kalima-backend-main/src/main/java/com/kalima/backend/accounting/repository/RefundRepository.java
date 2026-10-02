package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByCompany_IdAndId(UUID companyId, UUID id);

    @Query("""
            SELECT r FROM Refund r
            WHERE r.company.id = :companyId
              AND (:clientId IS NULL OR r.client.id = :clientId)
              AND (:invoiceId IS NULL OR r.invoice.id = :invoiceId)
              AND r.refundDate BETWEEN :from AND :to
            ORDER BY r.refundDate DESC, r.id
            """)
    List<Refund> search(@Param("companyId") UUID companyId,
                        @Param("clientId") UUID clientId,
                        @Param("invoiceId") UUID invoiceId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM Refund r
            WHERE r.invoice.id = :invoiceId
            """)
    java.math.BigDecimal sumByInvoiceId(@Param("invoiceId") UUID invoiceId);
}