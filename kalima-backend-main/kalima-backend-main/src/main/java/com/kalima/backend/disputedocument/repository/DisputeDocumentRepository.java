package com.kalima.backend.disputedocument.repository;

import com.kalima.backend.disputedocument.entity.DisputeDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeDocumentRepository extends JpaRepository<DisputeDocument, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy", "dispute", "stage"})
    @Query("SELECT d FROM DisputeDocument d WHERE d.id = :id")
    Optional<DisputeDocument> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"uploadedBy", "dispute", "stage"})
    @Query("""
            SELECT d FROM DisputeDocument d
            WHERE d.dispute.id = :disputeId
              AND (:stageId IS NULL OR d.stage.id = :stageId)
            """)
    List<DisputeDocument> findByDisputeAndOptionalStage(
            @Param("disputeId") UUID disputeId,
            @Param("stageId") UUID stageId,
            Sort sort);
}
