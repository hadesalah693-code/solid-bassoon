package com.kalima.backend.dispute.repository;

import com.kalima.backend.dispute.entity.DisputeStageAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeStageActionRepository extends JpaRepository<DisputeStageAction, UUID> {

    Optional<DisputeStageAction> findByIdAndDispute_Id(UUID id, UUID disputeId);

    @Query("""
            SELECT a FROM DisputeStageAction a
            LEFT JOIN FETCH a.assignedTo
            LEFT JOIN FETCH a.createdBy
            LEFT JOIN FETCH a.stage
            WHERE a.dispute.id = :disputeId
              AND (:stageId IS NULL OR (a.stage IS NOT NULL AND a.stage.id = :stageId))
            ORDER BY CASE a.status
                WHEN 'OPEN' THEN 1
                WHEN 'IN_PROGRESS' THEN 2
                WHEN 'DONE' THEN 3
                WHEN 'CANCELLED' THEN 4
                ELSE 5 END,
                     a.dueAt ASC NULLS LAST,
                     a.createdAt DESC
            """)
    List<DisputeStageAction> findForDisputeOrdered(
            @Param("disputeId") UUID disputeId,
            @Param("stageId") UUID stageId);

    @Query("""
            SELECT a FROM DisputeStageAction a
            LEFT JOIN FETCH a.assignedTo
            LEFT JOIN FETCH a.createdBy
            LEFT JOIN FETCH a.stage
            WHERE a.stage IS NOT NULL AND a.stage.id = :stageId
            ORDER BY a.dueAt ASC NULLS LAST, a.createdAt DESC
            """)
    List<DisputeStageAction> findForStageOrdered(@Param("stageId") UUID stageId);

    /**
     * Returns counts per status bucket in a single native SQL query.
     * Each result row is Object[]{bucket VARCHAR, count BIGINT} where
     * bucket is one of: "OPEN_TOTAL", "OVERDUE", "DONE", "CANCELLED", "OTHER".
     *
     * <p>Native SQL is used because JPQL does not support GROUP BY on an
     * inline CASE expression.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                CASE
                    WHEN status IN ('OPEN','IN_PROGRESS')
                         AND due_at IS NOT NULL
                         AND due_at < CAST(:now AS TIMESTAMP) THEN 'OVERDUE'
                    WHEN status IN ('OPEN','IN_PROGRESS')    THEN 'OPEN_TOTAL'
                    WHEN status = 'DONE'                     THEN 'DONE'
                    WHEN status = 'CANCELLED'                THEN 'CANCELLED'
                    ELSE 'OTHER'
                END      AS bucket,
                COUNT(*) AS cnt
            FROM dispute_stage_actions
            WHERE dispute_id = CAST(:disputeId AS UUID)
              AND company_id = CAST(:companyId AS UUID)
            GROUP BY bucket
            """)
    List<Object[]> countByStatusBucket(
            @Param("disputeId") UUID disputeId,
            @Param("companyId") UUID companyId,
            @Param("now") LocalDateTime now);
}
