package com.kalima.backend.mandate.repository;

import com.kalima.backend.mandate.entity.MandateDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MandateDocumentRepository extends JpaRepository<MandateDocument, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy", "mandate"})
    @Query("SELECT d FROM MandateDocument d WHERE d.id = :id")
    Optional<MandateDocument> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"uploadedBy"})
    List<MandateDocument> findByMandate_Id(UUID mandateId, Sort sort);
}
