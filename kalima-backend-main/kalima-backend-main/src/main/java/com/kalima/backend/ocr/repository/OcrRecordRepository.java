package com.kalima.backend.ocr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.ocr.entity.OcrRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OcrRecordRepository extends JpaRepository<OcrRecord, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy", "relations"})
    @Query("SELECT r FROM OcrRecord r WHERE r.id = :id")
    Optional<OcrRecord> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"uploadedBy", "relations"})
    @Query("""
            SELECT DISTINCT r FROM OcrRecord r
            LEFT JOIN r.relations rel
            WHERE (:status IS NULL OR r.status = :status)
              AND (:documentType IS NULL OR r.documentType = :documentType)
              AND (:uploadedBy IS NULL OR r.uploadedBy.id = :uploadedBy)
              AND (:fromAt IS NULL OR r.createdAt >= :fromAt)
              AND (:toAt IS NULL OR r.createdAt < :toAt)
              AND (
                    :privileged = TRUE
                    OR r.uploadedBy.id = :personId
                    OR (
                        rel.relationType = :caseRelationType
                        AND (
                            EXISTS (
                                SELECT c FROM com.kalima.backend.casefile.entity.CaseFile c
                                WHERE c.id = rel.relationId
                                  AND c.assignedLawyer.id = :personId
                            )
                            OR EXISTS (
                                SELECT p FROM com.kalima.backend.caseparticipant.entity.CaseParticipant p
                                WHERE p.caseFile.id = rel.relationId
                                  AND p.person.id = :personId
                            )
                        )
                    )
              )
            """)
    Page<OcrRecord> searchAccessible(
            @Param("privileged") boolean privileged,
            @Param("personId") UUID personId,
            @Param("caseRelationType") DomainEnums.OcrRelationType caseRelationType,
            @Param("status") DomainEnums.OcrStatus status,
            @Param("documentType") DomainEnums.OcrDocumentType documentType,
            @Param("uploadedBy") UUID uploadedBy,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            Pageable pageable);

    @EntityGraph(attributePaths = {"uploadedBy", "relations"})
    @Query("SELECT r FROM OcrRecord r WHERE r.id IN :ids")
    List<OcrRecord> findAllScopedByIds(@Param("ids") List<UUID> ids);
}
