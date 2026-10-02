package com.kalima.backend.delegation.repository;

import com.kalima.backend.delegation.entity.DelegationDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DelegationDocumentRepository extends JpaRepository<DelegationDocument, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy", "delegation"})
    @Query("SELECT d FROM DelegationDocument d WHERE d.id = :id")
    Optional<DelegationDocument> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"uploadedBy"})
    List<DelegationDocument> findByDelegation_Id(UUID delegationId, Sort sort);
}
