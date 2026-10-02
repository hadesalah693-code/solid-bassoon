package com.kalima.backend.dispute.repository;

import com.kalima.backend.dispute.entity.DisputeParty;
import com.kalima.backend.domain.entity.DomainEnums;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputePartyRepository extends JpaRepository<DisputeParty, UUID> {

    @Query("SELECT p FROM DisputeParty p WHERE p.id = :id")
    Optional<DisputeParty> findScopedById(@Param("id") UUID id);

    List<DisputeParty> findByDispute_IdOrderBySortOrderAscCreatedAtAsc(UUID disputeId);

    boolean existsByDispute_IdAndPerson_IdAndRole(
            UUID disputeId,
            UUID personId,
            DomainEnums.DisputePartyRole role);

    boolean existsByDispute_IdAndPerson_IdAndRoleAndIdNot(
            UUID disputeId,
            UUID personId,
            DomainEnums.DisputePartyRole role,
            UUID excludedPartyId);
}
