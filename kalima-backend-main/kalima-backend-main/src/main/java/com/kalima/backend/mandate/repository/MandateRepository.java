package com.kalima.backend.mandate.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.mandate.entity.Mandate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MandateRepository
        extends JpaRepository<Mandate, UUID>, JpaSpecificationExecutor<Mandate> {

    /** Tenant-scoped primary-key lookup via JPQL (forces Hibernate @Filter). */
    @EntityGraph(attributePaths = {
            "caseFile", "caseFile.client", "originalLawyer", "substitutedLawyer",
            "client", "hearing", "nextHearing", "createdBy"
    })
    @Query("SELECT m FROM Mandate m WHERE m.id = :id")
    Optional<Mandate> findScopedById(@Param("id") UUID id);

    /** List rows with a specific status (session approaching / upcoming). */
    @EntityGraph(attributePaths = {
            "caseFile", "client", "originalLawyer", "substitutedLawyer"
    })
    @Query("""
            SELECT m FROM Mandate m
            WHERE m.status IN :statuses
                                                        AND m.deletedAt IS NULL
              AND m.sessionDate >= :fromDate
            ORDER BY m.sessionDate ASC
            """)
    Page<Mandate> findUpcomingByStatuses(
            @Param("statuses") java.util.Collection<DomainEnums.MandateStatus> statuses,
            @Param("fromDate") LocalDateTime fromDate,
            Pageable pageable);
}
