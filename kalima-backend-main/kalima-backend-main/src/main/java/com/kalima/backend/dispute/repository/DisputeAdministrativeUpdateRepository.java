package com.kalima.backend.dispute.repository;

import com.kalima.backend.dispute.entity.DisputeAdministrativeUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisputeAdministrativeUpdateRepository extends JpaRepository<DisputeAdministrativeUpdate, UUID> {

    List<DisputeAdministrativeUpdate> findByDispute_IdOrderByCreatedAtDesc(UUID disputeId);

    boolean existsByDispute_IdAndUpdateType(UUID disputeId, String updateType);
}
