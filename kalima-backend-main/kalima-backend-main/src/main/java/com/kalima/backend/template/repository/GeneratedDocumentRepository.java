package com.kalima.backend.template.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.template.entity.GeneratedDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link GeneratedDocument}.
 *
 * <p>Tenant isolation comes from the Hibernate {@code tenantFilter} declared
 * on the entity, so callers do not need to add {@code company_id} clauses
 * here.
 */
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

    @EntityGraph(attributePaths = {"template", "client", "caseFile", "company"})
    @Query("SELECT g FROM GeneratedDocument g WHERE g.id = :id")
    Optional<GeneratedDocument> findScopedById(@Param("id") UUID id);

    /**
     * List generated documents in the current tenant, optionally narrowed by
     * status, owning case, or owning client. Any combination of the
     * parameters can be {@code null}.
     */
    @EntityGraph(attributePaths = {"template", "client", "caseFile"})
    @Query("""
            SELECT g FROM GeneratedDocument g
            WHERE (:status   IS NULL OR g.status = :status)
              AND (:caseId   IS NULL OR g.caseFile.id = :caseId)
              AND (:clientId IS NULL OR g.client.id = :clientId)
            """)
    List<GeneratedDocument> search(
            @Param("status") DomainEnums.DocumentStatus status,
            @Param("caseId") UUID caseId,
            @Param("clientId") UUID clientId,
            Sort sort);
}
