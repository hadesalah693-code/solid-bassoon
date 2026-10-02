package com.kalima.backend.casedocument.repository;

import com.kalima.backend.casedocument.entity.CaseDocument;
import com.kalima.backend.domain.entity.DomainEnums;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseDocumentRepository extends JpaRepository<CaseDocument, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy", "caseFile"})
    @Query("SELECT d FROM CaseDocument d WHERE d.id = :id")
    Optional<CaseDocument> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"uploadedBy", "caseFile"})
    @Query("""
            SELECT d FROM CaseDocument d
            WHERE d.caseFile.id = :caseId
              AND (:visibility IS NULL OR d.visibility = :visibility)
            """)
    List<CaseDocument> findByCaseId(
            @Param("caseId") UUID caseId,
            @Param("visibility") DomainEnums.CaseDocumentVisibility visibility,
            Sort sort);
}
