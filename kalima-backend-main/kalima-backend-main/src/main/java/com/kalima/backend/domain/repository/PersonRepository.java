package com.kalima.backend.domain.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    Page<Person> findBySystemRole(DomainEnums.SystemRole systemRole, Pageable pageable);

    /** Default list: only persons not yet archived. */
    Page<Person> findByArchivedAtIsNull(Pageable pageable);

    /** Filter by role, excluding archived rows. */
    Page<Person> findBySystemRoleAndArchivedAtIsNull(
            DomainEnums.SystemRole systemRole, Pageable pageable);

    // Used by Staff listing: "all persons except CLIENT".
    Page<Person> findBySystemRoleNot(DomainEnums.SystemRole excluded, Pageable pageable);

    /** Staff list: non-clients, not archived. */
    Page<Person> findBySystemRoleNotAndArchivedAtIsNull(
            DomainEnums.SystemRole excluded, Pageable pageable);

    Optional<Person> findByEmail(String email);

    /**
     * Eagerly loads the tenant; used on the request thread (JWT filter) where no
     * service-layer transaction and tenant Hibernate filter are active.
     */
    @Query("SELECT p FROM Person p JOIN FETCH p.company WHERE p.email = :email")
    Optional<Person> findByEmailWithCompany(@Param("email") String email);

    /**
     * Eagerly loads the tenant by primary key; used during refresh-token rotation
     * where no JWT is present and the tenant Hibernate filter is not yet active.
     */
    @Query("SELECT p FROM Person p JOIN FETCH p.company WHERE p.id = :id")
    Optional<Person> findByIdWithCompany(@Param("id") UUID id);

    boolean existsByEmail(String email);

    // Primary-key lookups that respect the Hibernate {@code tenantFilter}.
    // Spring Data's default {@code findById} / {@code existsById} go through
    // {@code EntityManager#find} which bypasses entity-level @Filter
    // annotations — a well-known Hibernate behaviour. Using explicit JPQL
    // forces the filter to be applied.

    @Query("SELECT p FROM Person p WHERE p.id = :id")
    Optional<Person> findScopedById(@Param("id") UUID id);

    @Query("SELECT COUNT(p) > 0 FROM Person p WHERE p.id = :id")
    boolean existsScopedById(@Param("id") UUID id);

    /** Batch variant of {@link #findScopedById(UUID)}; avoids N+1 lookups when resolving sets of ids. */
    @Query("SELECT p FROM Person p WHERE p.id IN :ids")
    List<Person> findScopedByIdIn(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT p FROM Person p
            WHERE p.archivedAt IS NULL
              AND p.systemRole IN :roles
              AND LOWER(TRIM(p.fullName)) = LOWER(TRIM(:fullName))
            """)
    List<Person> findActiveStaffByExactFullName(
            @Param("fullName") String fullName,
            @Param("roles") Collection<DomainEnums.SystemRole> roles);

    @Query("""
            SELECT p FROM Person p
            WHERE p.archivedAt IS NULL
              AND p.systemRole IN :roles
            """)
    List<Person> findActiveStaffByRoles(@Param("roles") Collection<DomainEnums.SystemRole> roles);

    /**
     * Non-archived persons, optional role, case-insensitive substring on main
     * identity fields (free-text search in the UI).
     */
    @Query("""
            SELECT p FROM Person p
            WHERE p.archivedAt IS NULL
              AND (:role IS NULL OR p.systemRole = :role)
              AND (
                    LOWER(COALESCE(p.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(p.companyName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(p.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(p.phone, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(p.nationalId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(p.commercialRegister, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<Person> searchActive(
            @Param("role") DomainEnums.SystemRole role,
            @Param("search") String search,
            Pageable pageable);
}
