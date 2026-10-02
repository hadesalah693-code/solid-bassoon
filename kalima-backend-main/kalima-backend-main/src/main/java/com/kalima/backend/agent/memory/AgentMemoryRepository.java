package com.kalima.backend.agent.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentMemoryRepository extends JpaRepository<AgentMemory, UUID> {

    @Query("""
            SELECT m FROM AgentMemory m
            WHERE m.person.id = :personId AND m.memoryKey = :key
            """)
    Optional<AgentMemory> findByPersonAndKey(
            @Param("personId") UUID personId,
            @Param("key") String key);

    @Query("""
            SELECT m FROM AgentMemory m
            WHERE m.person.id = :personId
            ORDER BY m.updatedAt DESC
            """)
    List<AgentMemory> findAllForPerson(@Param("personId") UUID personId);
}
