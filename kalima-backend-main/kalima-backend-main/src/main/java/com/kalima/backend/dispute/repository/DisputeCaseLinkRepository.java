package com.kalima.backend.dispute.repository;

import com.kalima.backend.dispute.entity.DisputeCaseLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeCaseLinkRepository extends JpaRepository<DisputeCaseLink, UUID> {

    List<DisputeCaseLink> findByDispute_IdOrderByCreatedAtAsc(UUID disputeId);

    boolean existsByDispute_IdAndCaseFile_Id(UUID disputeId, UUID caseId);

    Optional<DisputeCaseLink> findByDispute_IdAndCaseFile_Id(UUID disputeId, UUID caseId);

    void deleteByDispute_IdAndCaseFile_Id(UUID disputeId, UUID caseId);
}
