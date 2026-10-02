package com.kalima.backend.memo.repository;

import com.kalima.backend.memo.entity.Memo;
import com.kalima.backend.memo.entity.MemoStatus;
import com.kalima.backend.memo.entity.MemoType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MemoRepository extends JpaRepository<Memo, UUID> {

    @EntityGraph(attributePaths = {"caseFile", "author", "reviewer"})
    @Query("SELECT m FROM Memo m WHERE m.id = :id AND m.company.id = :companyId")
    Optional<Memo> findScopedById(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @EntityGraph(attributePaths = {"caseFile", "author", "reviewer"})
    @Query("""
            SELECT m FROM Memo m
            WHERE m.company.id = :companyId
              AND (:caseId IS NULL OR m.caseFile.id = :caseId)
              AND (:status IS NULL OR m.status = :status)
              AND (:memoType IS NULL OR m.memoType = :memoType)
              AND (:search IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(m.content) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:privileged = TRUE OR m.caseFile.assignedLawyer.id = :actorId
                   OR m.author.id = :actorId
                   OR EXISTS (SELECT p.id FROM CaseParticipant p
                       WHERE p.caseFile = m.caseFile AND p.person.id = :actorId))
            ORDER BY m.updatedAt DESC
            """)
    Page<Memo> search(
            @Param("companyId") UUID companyId,
            @Param("caseId") UUID caseId,
            @Param("status") MemoStatus status,
            @Param("memoType") MemoType memoType,
            @Param("search") String search,
            @Param("actorId") UUID actorId,
            @Param("privileged") boolean privileged,
            Pageable pageable);
}