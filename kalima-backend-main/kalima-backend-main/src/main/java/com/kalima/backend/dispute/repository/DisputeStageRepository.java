package com.kalima.backend.dispute.repository;

import com.kalima.backend.dispute.entity.DisputeStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeStageRepository extends JpaRepository<DisputeStage, UUID> {

    List<DisputeStage> findByDispute_IdOrderByCreatedAtAsc(UUID disputeId);

    Optional<DisputeStage> findByIdAndDispute_Id(UUID id, UUID disputeId);
}
