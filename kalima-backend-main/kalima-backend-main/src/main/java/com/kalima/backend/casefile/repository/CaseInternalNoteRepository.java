package com.kalima.backend.casefile.repository;

import com.kalima.backend.casefile.entity.CaseInternalNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CaseInternalNoteRepository extends JpaRepository<CaseInternalNote, UUID> {

    @EntityGraph(attributePaths = "author")
    @Query("SELECT n FROM CaseInternalNote n WHERE n.caseFile.id = :caseId ORDER BY n.createdAt DESC")
    Page<CaseInternalNote> findForCase(@Param("caseId") UUID caseId, Pageable pageable);
}