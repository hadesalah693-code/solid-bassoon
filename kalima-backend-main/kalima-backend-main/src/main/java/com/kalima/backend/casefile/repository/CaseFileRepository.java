package com.kalima.backend.casefile.repository;

import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.DomainEnums.CasePriority;
import com.kalima.backend.domain.entity.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CaseFileRepository extends JpaRepository<CaseFile, UUID> {

    /**
     * Active matters: not archived, status in the open/ongoing set (excludes
     * {@link DomainEnums.CaseStatus#CLOSED} and {@code ARCHIVED} by archive
     * flag; CLOSED is included only when the row is not archived per search
     * conventions elsewhere).
     */
    long countByArchivedAtIsNullAndStatusIn(List<DomainEnums.CaseStatus> statuses);

    Optional<CaseFile> findByCaseNumber(String caseNumber);

    /**
     * Tenant-scoped primary-key lookup.
     *
     * <p>{@link JpaRepository#findById(Object)} delegates to
     * {@code EntityManager.find()} which bypasses Hibernate @Filter, so a JWT
     * from tenant A could otherwise load a row from tenant B by guessing its
     * UUID. Using a JPQL query forces the tenantFilter to apply.
     */
    @EntityGraph(attributePaths = {"client", "assignedLawyer", "createdBy"})
    @Query("SELECT c FROM CaseFile c WHERE c.id = :id")
    Optional<CaseFile> findScopedById(@Param("id") UUID id);

    /**
     * Case-number uniqueness is scoped to the current tenant automatically by
     * the Hibernate tenantFilter, even though the column itself is just
     * {@code case_number}. The DB also backs this with the composite unique
     * constraint {@code uq_cases_company_case_number}.
     */
    @Query("SELECT COUNT(c) > 0 FROM CaseFile c WHERE c.caseNumber = :caseNumber")
    boolean existsByCaseNumberScoped(@Param("caseNumber") String caseNumber);

        @EntityGraph(attributePaths = {"client", "assignedLawyer", "createdBy", "parentCase"})
        @Query("SELECT c FROM CaseFile c WHERE c.parentCase.id = :parentId ORDER BY c.createdAt DESC")
        Page<CaseFile> findRelatedCases(@Param("parentId") UUID parentId, Pageable pageable);

    @Query("""
            SELECT COUNT(c) > 0 FROM CaseFile c
            WHERE c.client.id = :personId
               OR c.assignedLawyer.id = :personId
               OR c.createdBy.id = :personId
            """)
    boolean existsCaseReferencingPerson(@Param("personId") UUID personId);

    /**
     * Listing with optional structural filters (no free-text search).
     *
     * <p>Null parameters act as "no filter".
     * {@code includeArchived=false} hides rows with a non-null archived_at.
     *
     * <p>Split from {@link #searchWithText} because Postgres cannot infer the
     * type of a null parameter passed to {@code lower()} — it defaults to
     * bytea and the query fails at runtime.
     *
     * <p>{@code @EntityGraph}: {@link com.kalima.backend.casefile.service.CaseService#toResponse}
     * needs client, assigned lawyer, and creator for every row; loading here avoids
     * N+1 lazy queries when {@code spring.jpa.open-in-view=false}.
     */
    @EntityGraph(attributePaths = {"client", "assignedLawyer", "createdBy"})
    @Query("""
            SELECT c FROM CaseFile c
            WHERE (:status IS NULL OR c.status = :status)
                                                        AND (:priority IS NULL OR c.priority = :priority)
                                                        AND (:fromDate IS NULL OR c.startDate >= :fromDate)
                                                        AND (:toDate IS NULL OR c.startDate <= :toDate)
              AND (:clientId IS NULL OR c.client.id = :clientId)
              AND (:assignedLawyerId IS NULL OR c.assignedLawyer.id = :assignedLawyerId)
                          AND (:actorCanSeeAll = TRUE OR
                                   (:clientActor = TRUE AND c.client.id = :actorId) OR
                                   (:clientActor = FALSE AND (
                                           c.assignedLawyer.id = :actorId OR EXISTS (
                                                   SELECT p.id FROM CaseParticipant p
                                                   WHERE p.caseFile = c AND p.person.id = :actorId))))
              AND (:includeArchived = TRUE OR c.archivedAt IS NULL)
            """)
    Page<CaseFile> searchWithoutText(
            @Param("status") DomainEnums.CaseStatus status,
            @Param("priority") CasePriority priority,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("clientId") UUID clientId,
            @Param("assignedLawyerId") UUID assignedLawyerId,
            @Param("actorId") UUID actorId,
            @Param("actorCanSeeAll") boolean actorCanSeeAll,
            @Param("clientActor") boolean clientActor,
            @Param("includeArchived") boolean includeArchived,
            Pageable pageable);

    /**
     * Same as {@link #searchWithoutText} plus case-insensitive substring match
     * on title or case number. {@code search} must be non-null here.
     *
     * <p>Same {@code @EntityGraph} as {@link #searchWithoutText} for list DTO mapping.
     */
    @EntityGraph(attributePaths = {"client", "assignedLawyer", "createdBy"})
    @Query("""
            SELECT c FROM CaseFile c
            WHERE (:status IS NULL OR c.status = :status)
                                                        AND (:priority IS NULL OR c.priority = :priority)
                                                        AND (:fromDate IS NULL OR c.startDate >= :fromDate)
                                                        AND (:toDate IS NULL OR c.startDate <= :toDate)
              AND (:clientId IS NULL OR c.client.id = :clientId)
              AND (:assignedLawyerId IS NULL OR c.assignedLawyer.id = :assignedLawyerId)
                          AND (:actorCanSeeAll = TRUE OR
                                   (:clientActor = TRUE AND c.client.id = :actorId) OR
                                   (:clientActor = FALSE AND (
                                           c.assignedLawyer.id = :actorId OR EXISTS (
                                                   SELECT p.id FROM CaseParticipant p
                                                   WHERE p.caseFile = c AND p.person.id = :actorId))))
              AND (LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR (c.courtCaseNumber IS NOT NULL
                       AND LOWER(c.courtCaseNumber) LIKE LOWER(CONCAT('%', :search, '%')))
                   OR LOWER(c.client.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR (c.client.companyName IS NOT NULL
                       AND LOWER(c.client.companyName) LIKE LOWER(CONCAT('%', :search, '%')))
                   OR (c.description IS NOT NULL
                       AND LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))))
              AND (:includeArchived = TRUE OR c.archivedAt IS NULL)
            """)
    Page<CaseFile> searchWithText(
            @Param("status") DomainEnums.CaseStatus status,
            @Param("priority") CasePriority priority,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("clientId") UUID clientId,
            @Param("assignedLawyerId") UUID assignedLawyerId,
            @Param("actorId") UUID actorId,
            @Param("actorCanSeeAll") boolean actorCanSeeAll,
            @Param("clientActor") boolean clientActor,
            @Param("search") String search,
            @Param("includeArchived") boolean includeArchived,
            Pageable pageable);

    /* ────────────────────────────────────────────────────────────
       Chat-scope queries (used by ChatScopeService).

       The {@code privileged} flag is the bypass for ADMIN / SENIOR_LAWYER:
       when {@code true} every non-archived case in the tenant qualifies.
       Otherwise only cases where the actor is the assigned lawyer OR a
       case_participants row exists for them are returned.

       Tenant isolation is enforced by the Hibernate {@code tenantFilter}
       activated by {@code TenantFilterAspect} around every service call.
       ──────────────────────────────────────────────────────────── */

    /**
     * Distinct CLIENT persons attached as primary client to any case the
     * actor can chat in. Excludes archived cases.
     */
    @Query("""
            SELECT DISTINCT c.client FROM CaseFile c
            WHERE c.archivedAt IS NULL
              AND c.client IS NOT NULL
              AND (:privileged = TRUE
                   OR (c.assignedLawyer IS NOT NULL
                        AND c.assignedLawyer.id = :actorId)
                   OR EXISTS (SELECT 1 FROM CaseParticipant p
                              WHERE p.caseFile = c
                                AND p.person.id = :actorId))
            """)
    List<Person> findDistinctClientsAccessibleForChat(
            @Param("privileged") boolean privileged,
            @Param("actorId") UUID actorId);

    /**
     * Cases for the given client that the actor can chat in. Excludes
     * archived cases. Result is sorted most-recently-updated first.
     */
    @EntityGraph(attributePaths = {"client", "assignedLawyer", "createdBy"})
    @Query("""
            SELECT c FROM CaseFile c
            WHERE c.archivedAt IS NULL
              AND c.client IS NOT NULL
              AND c.client.id = :clientId
              AND (:privileged = TRUE
                   OR (c.assignedLawyer IS NOT NULL
                        AND c.assignedLawyer.id = :actorId)
                   OR EXISTS (SELECT 1 FROM CaseParticipant p
                              WHERE p.caseFile = c
                                AND p.person.id = :actorId))
            ORDER BY c.updatedAt DESC
            """)
    List<CaseFile> findCasesAccessibleForChatByClient(
            @Param("clientId") UUID clientId,
            @Param("privileged") boolean privileged,
            @Param("actorId") UUID actorId);

    /**
     * All non-archived cases accessible to the actor (tenant-scoped via
     * Hibernate filter). Used by the messages inbox to pre-load conversation
     * summaries without a clientId filter.
     *
     * <p>CLIENT actors only see their own cases (c.client.id = actorId).
     * LAWYER actors see cases they are assigned to or participate in.
     * ADMIN / SENIOR_LAWYER (privileged=true) see everything in the tenant.
     */
    @EntityGraph(attributePaths = {"client", "assignedLawyer"})
    @Query("""
            SELECT c FROM CaseFile c
            WHERE c.archivedAt IS NULL
              AND (:privileged = TRUE
                   OR c.client.id = :actorId
                   OR (c.assignedLawyer IS NOT NULL
                        AND c.assignedLawyer.id = :actorId)
                   OR EXISTS (SELECT 1 FROM CaseParticipant p
                              WHERE p.caseFile = c
                                AND p.person.id = :actorId))
            ORDER BY c.updatedAt DESC
            """)
    List<CaseFile> findAllCasesAccessibleForInbox(
            @Param("privileged") boolean privileged,
            @Param("actorId") UUID actorId);
}
