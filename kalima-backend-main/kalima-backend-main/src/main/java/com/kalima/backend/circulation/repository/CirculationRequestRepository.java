package com.kalima.backend.circulation.repository;

import com.kalima.backend.circulation.entity.CirculationRequest;
import com.kalima.backend.circulation.entity.CirculationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface CirculationRequestRepository extends JpaRepository<CirculationRequest, UUID> {
    @EntityGraph(attributePaths = {"caseFile", "fromPerson", "toPerson", "requestedBy", "approvedBy"})
    @Query("SELECT r FROM CirculationRequest r WHERE r.id = :id AND r.company.id = :companyId")
    Optional<CirculationRequest> findScoped(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @EntityGraph(attributePaths = {"caseFile", "fromPerson", "toPerson", "requestedBy", "approvedBy"})
    @Query("""
            SELECT r FROM CirculationRequest r
            WHERE r.company.id = :companyId AND r.toPerson.id = :personId
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.requestedAt DESC
            """)
    Page<CirculationRequest> inbox(
            @Param("companyId") UUID companyId, @Param("personId") UUID personId,
            @Param("status") CirculationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"caseFile", "fromPerson", "toPerson", "requestedBy", "approvedBy"})
    @Query("""
            SELECT r FROM CirculationRequest r
            WHERE r.company.id = :companyId
              AND (r.requestedBy.id = :personId OR r.fromPerson.id = :personId)
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.requestedAt DESC
            """)
    Page<CirculationRequest> outbox(
            @Param("companyId") UUID companyId, @Param("personId") UUID personId,
            @Param("status") CirculationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"caseFile", "fromPerson", "toPerson", "requestedBy", "approvedBy"})
    @Query("SELECT r FROM CirculationRequest r WHERE r.caseFile.id = :caseId ORDER BY r.requestedAt DESC")
    Page<CirculationRequest> findByCaseId(@Param("caseId") UUID caseId, Pageable pageable);
}