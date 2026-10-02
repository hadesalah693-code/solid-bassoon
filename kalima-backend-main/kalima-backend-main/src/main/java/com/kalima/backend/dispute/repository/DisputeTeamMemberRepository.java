package com.kalima.backend.dispute.repository;

import com.kalima.backend.dispute.entity.DisputeTeamMember;
import com.kalima.backend.domain.entity.DomainEnums.DisputeTeamRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeTeamMemberRepository extends JpaRepository<DisputeTeamMember, UUID> {

    @EntityGraph(attributePaths = {"person"})
    List<DisputeTeamMember> findByDispute_IdOrderByRoleAsc(UUID disputeId);

    @EntityGraph(attributePaths = {"person"})
    Optional<DisputeTeamMember> findByDispute_IdAndRole(UUID disputeId, DisputeTeamRole role);

    void deleteByDispute_IdAndRole(UUID disputeId, DisputeTeamRole role);

    @Query("SELECT m FROM DisputeTeamMember m JOIN FETCH m.person WHERE m.dispute.id = :disputeId")
    List<DisputeTeamMember> findByDisputeIdWithPerson(@Param("disputeId") UUID disputeId);
}
