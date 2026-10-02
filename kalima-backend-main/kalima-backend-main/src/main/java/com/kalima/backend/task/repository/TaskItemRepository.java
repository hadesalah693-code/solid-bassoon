package com.kalima.backend.task.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.task.entity.TaskItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TaskItemRepository extends JpaRepository<TaskItem, UUID> {

    @EntityGraph(attributePaths = {"createdBy", "assignedTo", "caseFile", "dispute"})
    @Query("SELECT t FROM TaskItem t WHERE t.id = :id")
    Optional<TaskItem> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"createdBy", "assignedTo", "caseFile", "dispute"})
    @Query("""
            SELECT t FROM TaskItem t
            WHERE t.caseFile.id = :caseId
            ORDER BY t.dueDate ASC NULLS LAST, t.createdAt DESC
            """)
    Page<TaskItem> findByCaseIdForList(@Param("caseId") UUID caseId, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "assignedTo", "caseFile", "dispute"})
    @Query("""
            SELECT t FROM TaskItem t
            WHERE t.dispute.id = :disputeId
            ORDER BY t.dueDate ASC NULLS LAST, t.createdAt DESC
            """)
    Page<TaskItem> findByDisputeIdForList(@Param("disputeId") UUID disputeId, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "assignedTo", "caseFile", "dispute"})
    @Query(
            value = """
                    SELECT t FROM TaskItem t
                    WHERE (:status IS NULL OR t.status = :status)
                      AND (:assignedToId IS NULL OR t.assignedTo.id = :assignedToId)
                      AND (:caseId IS NULL OR t.caseFile.id = :caseId)
                      AND (:disputeId IS NULL OR t.dispute.id = :disputeId)
                      AND (:unassignedOnly IS NULL OR :unassignedOnly = false OR t.assignedTo IS NULL)
                      AND (:adminTaskType IS NULL OR t.adminTaskType = :adminTaskType)
                    ORDER BY t.dueDate ASC NULLS LAST, t.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(t) FROM TaskItem t
                    WHERE (:status IS NULL OR t.status = :status)
                      AND (:assignedToId IS NULL OR t.assignedTo.id = :assignedToId)
                      AND (:caseId IS NULL OR t.caseFile.id = :caseId)
                      AND (:disputeId IS NULL OR t.dispute.id = :disputeId)
                      AND (:unassignedOnly IS NULL OR :unassignedOnly = false OR t.assignedTo IS NULL)
                      AND (:adminTaskType IS NULL OR t.adminTaskType = :adminTaskType)
                    """)
    Page<TaskItem> search(
            @Param("status") DomainEnums.TaskStatus status,
            @Param("assignedToId") UUID assignedToId,
            @Param("caseId") UUID caseId,
            @Param("disputeId") UUID disputeId,
            @Param("unassignedOnly") Boolean unassignedOnly,
            @Param("adminTaskType") DomainEnums.AdminTaskType adminTaskType,
            Pageable pageable);

    @Query("""
            SELECT COUNT(t) FROM TaskItem t
            WHERE t.assignedTo.id = :assigneeId
              AND t.status IN (:todo, :inProgress)
            """)
    long countOpenForAssignee(
            @Param("assigneeId") UUID assigneeId,
            @Param("todo") DomainEnums.TaskStatus todo,
            @Param("inProgress") DomainEnums.TaskStatus inProgress);

    @Query("""
            SELECT COUNT(t) FROM TaskItem t
            WHERE t.status IN (:todo, :inProgress)
              AND (t.caseFile IS NULL OR t.caseFile.archivedAt IS NULL)
            """)
    long countPendingOnActiveCases(
            @Param("todo") DomainEnums.TaskStatus todo,
            @Param("inProgress") DomainEnums.TaskStatus inProgress);

    @EntityGraph(attributePaths = {"assignedTo", "caseFile", "dispute", "company"})
    @Query("""
            SELECT t FROM TaskItem t
            WHERE t.status IN (:todo, :inProgress)
              AND t.assignedTo IS NOT NULL
              AND t.dueDate IS NOT NULL
              AND t.dueDate >= :tomorrowStart
              AND t.dueDate < :tomorrowEnd
              AND t.dueSoonNotifiedAt IS NULL
            """)
    java.util.List<TaskItem> findTasksDueTomorrow(
            @Param("todo") DomainEnums.TaskStatus todo,
            @Param("inProgress") DomainEnums.TaskStatus inProgress,
            @Param("tomorrowStart") java.time.LocalDateTime tomorrowStart,
            @Param("tomorrowEnd") java.time.LocalDateTime tomorrowEnd);

    @EntityGraph(attributePaths = {"assignedTo", "caseFile", "dispute", "company"})
    @Query("""
            SELECT t FROM TaskItem t
            WHERE t.status IN (:todo, :inProgress)
              AND t.assignedTo IS NOT NULL
              AND t.dueDate IS NOT NULL
              AND t.dueDate < :startOfToday
              AND (t.overdueNotifiedAt IS NULL OR t.overdueNotifiedAt < :startOfToday)
            """)
    java.util.List<TaskItem> findOverdueTasksNeedingReminder(
            @Param("todo") DomainEnums.TaskStatus todo,
            @Param("inProgress") DomainEnums.TaskStatus inProgress,
            @Param("startOfToday") java.time.LocalDateTime startOfToday);
}
