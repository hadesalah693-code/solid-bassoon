package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.ReceiptAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptAllocationRepository extends JpaRepository<ReceiptAllocation, UUID> {

    @Query("""
            SELECT a FROM ReceiptAllocation a
            WHERE a.company.id = :companyId AND a.receipt.id = :receiptId
            ORDER BY a.allocationDate, a.id
            """)
    List<ReceiptAllocation> findByCompanyIdAndReceiptId(
            @Param("companyId") UUID companyId, @Param("receiptId") UUID receiptId);

    @Query("""
            SELECT a FROM ReceiptAllocation a
            WHERE a.company.id = :companyId AND a.invoice.id = :invoiceId
            ORDER BY a.allocationDate DESC, a.id DESC
            """)
    List<ReceiptAllocation> findByCompanyIdAndInvoiceId(
            @Param("companyId") UUID companyId, @Param("invoiceId") UUID invoiceId);

    Optional<ReceiptAllocation> findByCompany_IdAndId(UUID companyId, UUID id);

    @Query("""
            SELECT COALESCE(SUM(a.amount), 0) FROM ReceiptAllocation a
            WHERE a.receipt.id = :receiptId AND a.voidedAt IS NULL
            """)
    BigDecimal sumLiveByReceiptId(@Param("receiptId") UUID receiptId);

    @Query("""
            SELECT a FROM ReceiptAllocation a
            WHERE a.company.id = :companyId AND a.invoice.id = :invoiceId
              AND a.voidedAt IS NULL
            ORDER BY a.allocationDate, a.id
            """)
    List<ReceiptAllocation> findLiveByCompanyIdAndInvoiceId(
            @Param("companyId") UUID companyId, @Param("invoiceId") UUID invoiceId);

    @Query("""
            SELECT a FROM ReceiptAllocation a
            WHERE a.company.id = :companyId AND a.voidedAt IS NULL
              AND a.allocationDate BETWEEN :from AND :to
            """)
    List<ReceiptAllocation> findLiveInPeriod(@Param("companyId") UUID companyId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);
}