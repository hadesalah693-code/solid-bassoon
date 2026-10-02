package com.kalima.backend.delegation.repository;

import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.domain.entity.DomainEnums;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DelegationRepository extends JpaRepository<Delegation, UUID>, JpaSpecificationExecutor<Delegation> {

    @EntityGraph(attributePaths = {"client", "agent"})
    @Query("SELECT d FROM Delegation d WHERE d.id = :id")
    Optional<Delegation> findScopedById(@Param("id") UUID id);

    @Query("""
            SELECT d FROM Delegation d
            WHERE d.status <> com.kalima.backend.domain.entity.DomainEnums.DelegationStatus.CANCELLED
              AND d.expiresAt >= :today
            """)
    List<Delegation> findEligibleForReminders(@Param("today") LocalDate today);
}
