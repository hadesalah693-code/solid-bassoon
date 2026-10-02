package com.kalima.backend.dispute.repository;

import com.kalima.backend.dispute.entity.DisputeTimelineEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DisputeTimelineEventRepository extends JpaRepository<DisputeTimelineEvent, UUID> {

    @EntityGraph(attributePaths = {"createdBy"})
    @Query("""
            SELECT e FROM DisputeTimelineEvent e
            WHERE e.dispute.id = :disputeId
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<DisputeTimelineEvent> findByDisputeIdForDetail(@Param("disputeId") UUID disputeId);
}
