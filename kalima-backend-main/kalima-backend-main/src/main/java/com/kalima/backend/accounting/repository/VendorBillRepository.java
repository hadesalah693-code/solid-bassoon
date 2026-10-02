package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.VendorBill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorBillRepository extends JpaRepository<VendorBill, UUID> {

    Optional<VendorBill> findByCompany_IdAndId(UUID companyId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM VendorBill b
            WHERE b.company.id = :companyId AND b.id = :id
            """)
    Optional<VendorBill> findForUpdateByCompanyIdAndId(@Param("companyId") UUID companyId,
                                                        @Param("id") UUID id);

    @Query("""
            SELECT b FROM VendorBill b
            WHERE b.company.id = :companyId
              AND (:vendorId IS NULL OR b.vendor.id = :vendorId)
              AND (:status IS NULL OR b.status = :status)
              AND b.billDate BETWEEN :from AND :to
            ORDER BY b.billDate DESC, b.id
            """)
    List<VendorBill> search(@Param("companyId") UUID companyId,
                            @Param("vendorId") UUID vendorId,
                            @Param("status") VendorBill.Status status,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(b.total - b.amountPaid), 0) FROM VendorBill b
            WHERE b.company.id = :companyId
              AND b.status IN (com.kalima.backend.accounting.entity.VendorBill.Status.OPEN,
                               com.kalima.backend.accounting.entity.VendorBill.Status.PARTIALLY_PAID)
            """)
    java.math.BigDecimal sumOutstanding(@Param("companyId") UUID companyId);

    /** Outstanding balance owed to one vendor, for the vendor's own summary. */
    @Query("""
            SELECT COALESCE(SUM(b.total - b.amountPaid), 0) FROM VendorBill b
            WHERE b.company.id = :companyId AND b.vendor.id = :vendorId
              AND b.status IN (com.kalima.backend.accounting.entity.VendorBill.Status.OPEN,
                               com.kalima.backend.accounting.entity.VendorBill.Status.PARTIALLY_PAID)
            """)
    java.math.BigDecimal sumOutstandingByVendor(@Param("companyId") UUID companyId,
                                                @Param("vendorId") UUID vendorId);

    @Query("""
            SELECT COUNT(b) FROM VendorBill b
            WHERE b.company.id = :companyId AND b.dueDate IS NOT NULL
              AND b.dueDate < :on
              AND b.status IN (com.kalima.backend.accounting.entity.VendorBill.Status.OPEN,
                               com.kalima.backend.accounting.entity.VendorBill.Status.PARTIALLY_PAID)
            """)
    long countOverdue(@Param("companyId") UUID companyId, @Param("on") LocalDate on);
}