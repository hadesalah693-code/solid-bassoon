package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.RecurringInvoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringInvoiceRepository extends JpaRepository<RecurringInvoice, UUID> {

    Optional<RecurringInvoice> findByCompany_IdAndId(UUID companyId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM RecurringInvoice r
            WHERE r.company.id = :companyId AND r.id = :id
            """)
    Optional<RecurringInvoice> findForUpdateByCompanyIdAndId(@Param("companyId") UUID companyId,
                                                              @Param("id") UUID id);

    @Query("""
            SELECT r FROM RecurringInvoice r
            WHERE r.company.id = :companyId AND r.client.id = :clientId
            ORDER BY r.active DESC, r.nextRunDate
            """)
    List<RecurringInvoice> findByCompanyIdAndClientId(@Param("companyId") UUID companyId,
                                                       @Param("clientId") UUID clientId);

    /** Every template for the company, active or not, active ones first. */
    @Query("""
            SELECT r FROM RecurringInvoice r
            WHERE r.company.id = :companyId
            ORDER BY r.active DESC, r.nextRunDate
            """)
    List<RecurringInvoice> findAllByCompany(@Param("companyId") UUID companyId);

    /** Active templates whose next run has come around, oldest first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM RecurringInvoice r
            WHERE r.company.id = :companyId
              AND r.active = TRUE
              AND r.nextRunDate <= :on
              AND (r.endDate IS NULL OR r.endDate >= :on)
            ORDER BY r.nextRunDate
            """)
    List<RecurringInvoice> findDueForUpdate(@Param("companyId") UUID companyId,
                                            @Param("on") LocalDate on);

    @Query("""
            SELECT r FROM RecurringInvoice r
            WHERE r.company.id = :companyId AND r.active = TRUE
              AND r.nextRunDate <= :on
              AND (r.endDate IS NULL OR r.endDate >= :on)
            """)
    List<RecurringInvoice> findDue(@Param("companyId") UUID companyId,
                                   @Param("on") LocalDate on);
}