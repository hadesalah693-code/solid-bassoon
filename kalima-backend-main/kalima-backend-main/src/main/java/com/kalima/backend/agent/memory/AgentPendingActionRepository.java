package com.kalima.backend.agent.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AgentPendingActionRepository extends JpaRepository<AgentPendingAction, UUID> {

    @Query("""
            SELECT p FROM AgentPendingAction p
            WHERE p.id = :id AND p.person.id = :personId
            """)
    Optional<AgentPendingAction> findScopedForPerson(
            @Param("id") UUID id,
            @Param("personId") UUID personId);
}
