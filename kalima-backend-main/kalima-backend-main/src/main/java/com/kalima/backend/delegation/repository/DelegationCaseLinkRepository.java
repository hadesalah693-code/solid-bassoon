package com.kalima.backend.delegation.repository;

import com.kalima.backend.delegation.entity.DelegationCaseLink;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DelegationCaseLinkRepository extends JpaRepository<DelegationCaseLink, UUID> {

    @EntityGraph(attributePaths = {"caseFile"})
    @Query("SELECT l FROM DelegationCaseLink l WHERE l.delegation.id = :delegationId ORDER BY l.createdAt ASC")
    List<DelegationCaseLink> findByDelegationIdWithCase(@Param("delegationId") UUID delegationId);

    boolean existsByDelegation_IdAndCaseFile_Id(UUID delegationId, UUID caseId);

    void deleteByDelegation_IdAndCaseFile_Id(UUID delegationId, UUID caseId);
}
