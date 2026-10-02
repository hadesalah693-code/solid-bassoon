package com.kalima.backend.agent.night;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface NightAgentRunRepository extends JpaRepository<NightAgentRun, UUID> {

    @Query("""
            SELECT r FROM NightAgentRun r
            ORDER BY r.ranAt DESC
            """)
    Optional<NightAgentRun> findLatest();
}
