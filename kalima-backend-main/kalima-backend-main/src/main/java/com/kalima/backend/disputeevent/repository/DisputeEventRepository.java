package com.kalima.backend.disputeevent.repository;

import com.kalima.backend.disputeevent.entity.DisputeEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DisputeEventRepository extends JpaRepository<DisputeEvent, UUID> {

    /**
     * Per-case cardinality for idempotent workflow seeding (workflow runs only when zero).
     */
    long countByCaseFile_Id(UUID caseId);

    /**
     * Tenant-scoped primary-key lookup (see {@code CaseFileRepository#findScopedById}).
     */
    @EntityGraph(attributePaths = {"caseFile"})
    @Query("SELECT e FROM DisputeEvent e WHERE e.id = :id")
    Optional<DisputeEvent> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"caseFile"})
    @Query("""
            SELECT e FROM DisputeEvent e
            WHERE e.caseFile.id = :caseId
            ORDER BY e.sortOrder ASC, e.eventDate ASC NULLS LAST, e.createdAt ASC
            """)
    Page<DisputeEvent> findByCaseIdForList(@Param("caseId") UUID caseId, Pageable pageable);
}
