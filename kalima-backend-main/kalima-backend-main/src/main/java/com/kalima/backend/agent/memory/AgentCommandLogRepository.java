package com.kalima.backend.agent.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgentCommandLogRepository extends JpaRepository<AgentCommandLog, UUID> {

    @Query("""
            SELECT l FROM AgentCommandLog l
            WHERE l.person.id = :personId
            ORDER BY l.createdAt DESC
            """)
    List<AgentCommandLog> findRecentForPerson(
            @Param("personId") UUID personId,
            org.springframework.data.domain.Pageable pageable);
}
