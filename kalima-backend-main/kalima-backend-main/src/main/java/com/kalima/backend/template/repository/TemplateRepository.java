package com.kalima.backend.template.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.template.entity.Template;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link Template}.
 *
 * <p>All queries rely on the active Hibernate {@code tenantFilter} (set on
 * the {@link Template} entity via {@code @Filter}) for tenant isolation, so
 * results are automatically scoped to the caller's company.
 */
public interface TemplateRepository extends JpaRepository<Template, UUID> {

    @EntityGraph(attributePaths = {"company"})
    @Query("SELECT t FROM Template t WHERE t.id = :id")
    Optional<Template> findScopedById(@Param("id") UUID id);

    /**
     * List templates of the current tenant, optionally filtered by type.
     * Pass {@code null} for {@code type} to return every template.
     */
    @Query("""
            SELECT t FROM Template t
            WHERE (:type IS NULL OR t.type = :type)
            """)
    List<Template> findAllByOptionalType(
            @Param("type") DomainEnums.TemplateType type,
            Sort sort);

    /**
     * Look up the default template for a given type within the current
     * tenant. Returns empty if no default has been marked.
     */
    @Query("""
            SELECT t FROM Template t
            WHERE t.type = :type
              AND t.isDefault = true
            """)
    Optional<Template> findDefaultByType(@Param("type") DomainEnums.TemplateType type);
}
