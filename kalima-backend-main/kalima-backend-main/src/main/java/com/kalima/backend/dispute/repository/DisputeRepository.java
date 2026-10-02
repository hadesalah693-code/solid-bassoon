package com.kalima.backend.dispute.repository;

import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.domain.entity.DomainEnums;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    @EntityGraph(attributePaths = {"client", "assignedLawyer", "convertedCase"})
    @Query("SELECT d FROM Dispute d WHERE d.id = :id")
    Optional<Dispute> findScopedById(@Param("id") UUID id);

    /**
     * Pagination + EntityGraph combined: Hibernate fetches the page IDs first,
     * then issues a second IN-query with the graph applied (no Cartesian product).
     * This avoids the HHH-90003004 "firstResult/maxResults specified with collection
     * fetch; applying in memory" warning while still preventing N+1 on client /
     * assignedLawyer / convertedCase.
     *
     * <p>Explicit {@code countQuery} avoids Hibernate/Spring Data bugs when deriving
     * a count from a paged query that includes {@code ORDER BY}.
     *
     * <p>Search pattern uses {@code LOWER(column) LIKE CONCAT('%', CAST(:search AS string), '%')}
     * (not {@code LOWER(CONCAT(...))}): PostgreSQL + Hibernate can otherwise bind {@code :search}
     * as {@code bytea}, causing {@code lower(bytea)} / {@code text ~~ bytea} errors.
     * Pass lowercase search from the service layer.
     */
    @EntityGraph(attributePaths = {"client", "assignedLawyer", "convertedCase"})
    @Query(
            value = """
                    SELECT d FROM Dispute d
                    WHERE (:status IS NULL OR d.status = :status)
                      AND (:disputeType IS NULL OR d.disputeType = :disputeType)
                      AND (:assignedLawyerId IS NULL OR d.assignedLawyer.id = :assignedLawyerId)
                      AND (:currentStep IS NULL OR d.currentStep = :currentStep)
                      AND (:search IS NULL
                           OR LOWER(d.disputeNumber) LIKE CONCAT('%', CAST(:search AS string), '%')
                           OR LOWER(d.title) LIKE CONCAT('%', CAST(:search AS string), '%')
                           OR LOWER(d.opponentName) LIKE CONCAT('%', CAST(:search AS string), '%')
                           OR (d.description IS NOT NULL
                               AND LOWER(d.description) LIKE CONCAT('%', CAST(:search AS string), '%'))
                           OR (d.client IS NOT NULL AND LOWER(d.client.fullName) LIKE CONCAT('%', CAST(:search AS string), '%'))
                           OR (d.client IS NOT NULL AND d.client.companyName IS NOT NULL
                               AND LOWER(d.client.companyName) LIKE CONCAT('%', CAST(:search AS string), '%')))
                    ORDER BY d.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(d) FROM Dispute d
                    WHERE (:status IS NULL OR d.status = :status)
                      AND (:disputeType IS NULL OR d.disputeType = :disputeType)
                      AND (:assignedLawyerId IS NULL OR d.assignedLawyer.id = :assignedLawyerId)
                      AND (:currentStep IS NULL OR d.currentStep = :currentStep)
                      AND (:search IS NULL
                           OR LOWER(d.disputeNumber) LIKE CONCAT('%', CAST(:search AS string), '%')
                           OR LOWER(d.title) LIKE CONCAT('%', CAST(:search AS string), '%')
                           OR LOWER(d.opponentName) LIKE CONCAT('%', CAST(:search AS string), '%')
                           OR (d.description IS NOT NULL
                               AND LOWER(d.description) LIKE CONCAT('%', CAST(:search AS string), '%'))
                           OR (d.client IS NOT NULL AND LOWER(d.client.fullName) LIKE CONCAT('%', CAST(:search AS string), '%'))
                           OR (d.client IS NOT NULL AND d.client.companyName IS NOT NULL
                               AND LOWER(d.client.companyName) LIKE CONCAT('%', CAST(:search AS string), '%')))
                    """)
    Page<Dispute> search(
            @Param("status") DomainEnums.DisputeStatus status,
            @Param("disputeType") DomainEnums.DisputeType disputeType,
            @Param("assignedLawyerId") UUID assignedLawyerId,
            @Param("currentStep") DomainEnums.DisputeWorkflowStep currentStep,
            @Param("search") String search,
            Pageable pageable);
}
