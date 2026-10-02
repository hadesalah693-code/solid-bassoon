package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.TimeEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    Optional<TimeEntry> findByCompany_IdAndId(UUID companyId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM TimeEntry t
            WHERE t.company.id = :companyId AND t.id = :id
            """)
    Optional<TimeEntry> findForUpdateByCompanyIdAndId(@Param("companyId") UUID companyId,
                                                       @Param("id") UUID id);

    @Query("""
            SELECT t FROM TimeEntry t
            WHERE t.company.id = :companyId
              AND (:caseId IS NULL OR t.caseFile.id = :caseId)
              AND (:lawyerId IS NULL OR t.lawyer.id = :lawyerId)
              AND (:from IS NULL OR t.workDate >= :from)
              AND (:to IS NULL OR t.workDate <= :to)
            ORDER BY t.workDate DESC, t.id
            """)
    List<TimeEntry> search(@Param("companyId") UUID companyId,
                           @Param("caseId") UUID caseId,
                           @Param("lawyerId") UUID lawyerId,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    /** Unbilled work on a case, oldest first, for turning into an invoice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM TimeEntry t
            WHERE t.company.id = :companyId
              AND t.caseFile.id = :caseId
              AND t.invoice.id IS NULL
              AND t.voidedAt IS NULL
            ORDER BY t.workDate, t.id
            """)
    List<TimeEntry> findUnbilledForUpdate(@Param("companyId") UUID companyId,
                                          @Param("caseId") UUID caseId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM TimeEntry t
            WHERE t.company.id = :companyId AND t.caseFile.id = :caseId
              AND t.invoice.id IS NULL AND t.voidedAt IS NULL AND t.billable = TRUE
            """)
    BigDecimal sumUnbilledByCase(@Param("companyId") UUID companyId, @Param("caseId") UUID caseId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM TimeEntry t
            WHERE t.company.id = :companyId AND t.caseFile.id = :caseId
              AND t.voidedAt IS NULL
            """)
    BigDecimal sumTotalByCase(@Param("companyId") UUID companyId, @Param("caseId") UUID caseId);
}