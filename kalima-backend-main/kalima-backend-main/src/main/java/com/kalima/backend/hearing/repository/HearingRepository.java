package com.kalima.backend.hearing.repository;

import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.hearing.entity.HearingStatus;
import com.kalima.backend.hearing.entity.HearingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HearingRepository extends JpaRepository<Hearing, UUID> {

    /**
     * Tenant-scoped primary-key lookup (see {@link CaseFileRepository#findScopedById}).
     */
    @EntityGraph(attributePaths = {"createdBy", "caseFile"})
    @Query("SELECT h FROM Hearing h WHERE h.id = :id")
    Optional<Hearing> findScopedById(@Param("id") UUID id);

        @Query("SELECT COUNT(h) > 0 FROM Hearing h WHERE h.previousHearing.id = :hearingId")
        boolean existsByPreviousHearingId(@Param("hearingId") UUID hearingId);

    // Each optional filter is written as COALESCE(<comparison>, TRUE) instead of
    // ":x IS NULL OR <comparison>". With the null test first, Hibernate emits the
    // parameter twice and the first binding is a bare "? IS NULL", which PostgreSQL
    // rejects with "could not determine data type of parameter $n" - a NULL comparison
    // carries no type for the driver to infer. Placed inside the comparison the
    // parameter is bound against a typed column, so the plan succeeds; when it is
    // null the comparison yields NULL and COALESCE turns that into TRUE, i.e. no filter.
    @Query("""
            SELECT h FROM Hearing h
            WHERE h.caseFile.id = :caseId
              AND COALESCE(h.hearingDate >= :fromDate, TRUE)
              AND COALESCE(h.hearingDate < :toDate, TRUE)
              AND COALESCE(h.status = :status, TRUE)
              AND COALESCE(h.hearingType = :type, TRUE)
              AND COALESCE(h.caseFile.assignedLawyer.id = :lawyerId, TRUE)
            ORDER BY h.hearingDate DESC
            """)
    @EntityGraph(attributePaths = {"createdBy", "caseFile", "previousHearing"})
    Page<Hearing> findByCaseIdForList(
            @Param("caseId") UUID caseId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("status") HearingStatus status,
            @Param("type") HearingType type,
            @Param("lawyerId") UUID lawyerId,
            Pageable pageable);

    @Query("""
            SELECT h FROM Hearing h
            WHERE h.caseFile.id = :caseId
            """)
    List<Hearing> findAllByCaseId(@Param("caseId") UUID caseId);

    /**
     * All hearings in the tenant for {@code [start, end)} on non-archived cases.
     * Sort is supplied via {@code Pageable} (typically by {@code hearingDate} asc).
     */
    @EntityGraph(attributePaths = {
            "caseFile", "caseFile.client", "caseFile.assignedLawyer", "createdBy"
    })
    @Query("""
            SELECT h FROM Hearing h
            WHERE h.hearingDate >= :start
              AND h.hearingDate < :end
              AND h.caseFile.archivedAt IS NULL
                                                        AND COALESCE(h.status = :status, TRUE)
                                                        AND COALESCE(h.hearingType = :type, TRUE)
                                                        AND COALESCE(h.caseFile.assignedLawyer.id = :lawyerId, TRUE)
            """)
    Page<Hearing> findOfficeHearingsForTimeRange(
                                                @Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("status") HearingStatus status,
                                                @Param("type") HearingType type,
                                                @Param("lawyerId") UUID lawyerId,
            Pageable pageable);

    /**
     * Hearings on {@code [start, end)} whose parent case is not archived. Uses
     * the JVM default time zone for calendar-day interpretation (same as the
     * rest of the service layer for {@link LocalDateTime} fields).
     */
    @Query("""
            SELECT COUNT(h) FROM Hearing h
            WHERE h.hearingDate >= :start
              AND h.hearingDate < :end
              AND h.caseFile.archivedAt IS NULL
            """)
    long countHearingsForActiveCasesBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
