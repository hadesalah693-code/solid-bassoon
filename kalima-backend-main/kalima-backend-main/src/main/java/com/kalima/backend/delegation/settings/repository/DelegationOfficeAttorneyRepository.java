package com.kalima.backend.delegation.settings.repository;

import com.kalima.backend.delegation.settings.entity.DelegationOfficeAttorney;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DelegationOfficeAttorneyRepository extends JpaRepository<DelegationOfficeAttorney, UUID> {

    @EntityGraph(attributePaths = {"person"})
    @Query("SELECT a FROM DelegationOfficeAttorney a WHERE a.id = :id")
    Optional<DelegationOfficeAttorney> findScopedById(@Param("id") UUID id);

    /** Admin management list: everyone, default first, then by person name. */
    @EntityGraph(attributePaths = {"person"})
    @Query("""
            SELECT a FROM DelegationOfficeAttorney a
            ORDER BY a.isDefault DESC, a.active DESC, a.person.fullName ASC
            """)
    List<DelegationOfficeAttorney> findAllOrdered();

    /** Delegation-creation dropdown: active only, default first, then by person name. */
    @EntityGraph(attributePaths = {"person"})
    @Query("""
            SELECT a FROM DelegationOfficeAttorney a
            WHERE a.active = true
            ORDER BY a.isDefault DESC, a.person.fullName ASC
            """)
    List<DelegationOfficeAttorney> findActiveOrdered();

    @Query("SELECT COUNT(a) > 0 FROM DelegationOfficeAttorney a WHERE a.person.id = :personId")
    boolean existsScopedByPersonId(@Param("personId") UUID personId);
}
