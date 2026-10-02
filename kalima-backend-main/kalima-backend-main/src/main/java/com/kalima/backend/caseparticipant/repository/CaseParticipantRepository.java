package com.kalima.backend.caseparticipant.repository;

import com.kalima.backend.caseparticipant.entity.CaseParticipant;
import com.kalima.backend.domain.entity.DomainEnums;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseParticipantRepository extends JpaRepository<CaseParticipant, UUID> {

    /**
     * Tenant-scoped primary-key lookup. JPQL so the Hibernate tenantFilter
     * applies (unlike {@code EntityManager.find}).
     */
    @Query("SELECT p FROM CaseParticipant p WHERE p.id = :id")
    Optional<CaseParticipant> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"person", "caseFile"})
    @Query("""
            SELECT p FROM CaseParticipant p
            WHERE p.caseFile.id = :caseId
            ORDER BY p.role ASC, p.createdAt ASC
            """)
    List<CaseParticipant> findByCaseId(@Param("caseId") UUID caseId);

    @Query("""
            SELECT COUNT(p) > 0 FROM CaseParticipant p
            WHERE p.caseFile.id = :caseId
              AND p.person.id = :personId
              AND p.role = :role
            """)
    boolean existsByCaseAndPersonAndRole(
            @Param("caseId") UUID caseId,
            @Param("personId") UUID personId,
            @Param("role") DomainEnums.CaseParticipantRole role);

    /**
     * Whether the person appears in {@code case_participants} for the case.
     * Mirrors the {@code EXISTS} branch in {@link
     * com.kalima.backend.casefile.repository.CaseFileRepository} inbox/chat
     * visibility queries.
     */
    @Query("""
            SELECT COUNT(p) > 0 FROM CaseParticipant p
            WHERE p.caseFile.id = :caseId
              AND p.person.id = :personId
            """)
    boolean existsByCaseIdAndPersonId(
            @Param("caseId") UUID caseId,
            @Param("personId") UUID personId);

    @Query("SELECT COUNT(p) > 0 FROM CaseParticipant p WHERE p.person.id = :personId")
    boolean existsByPersonId(@Param("personId") UUID personId);
}
