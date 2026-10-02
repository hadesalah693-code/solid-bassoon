package com.kalima.backend.calendar.repository;

import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.domain.entity.DomainEnums;
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

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    @EntityGraph(attributePaths = {
            "caseFile", "caseFile.client", "client", "assignedTo", "createdBy",
            "taskItem", "dispute"
    })
    @Query("SELECT e FROM CalendarEvent e WHERE e.id = :id")
    Optional<CalendarEvent> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {
            "caseFile", "caseFile.client", "client", "assignedTo", "createdBy",
            "taskItem", "dispute"
    })
    @Query("""
            SELECT e FROM CalendarEvent e
            WHERE e.sourceModule = :module AND e.sourceId = :sourceId
            """)
    Optional<CalendarEvent> findByProjection(
            @Param("module") DomainEnums.CalendarSourceModule module,
            @Param("sourceId") UUID sourceId);

    @EntityGraph(attributePaths = {
            "caseFile", "caseFile.client", "client", "assignedTo", "createdBy",
            "taskItem", "dispute"
    })
    @Query(
            value = """
                    SELECT e FROM CalendarEvent e
                    WHERE (
                        (e.startAt >= :rangeStart AND e.startAt < :rangeEnd)
                        OR (e.endAt IS NOT NULL AND e.endAt >= :rangeStart AND e.startAt < :rangeEnd)
                    )
                      AND (:eventType IS NULL OR e.eventType = :eventType)
                      AND (:status IS NULL OR e.status = :status)
                      AND (:caseId IS NULL OR e.caseFile.id = :caseId)
                      AND (:clientId IS NULL OR e.client.id = :clientId
                           OR e.caseFile.client.id = :clientId)
                      AND (:assignedToId IS NULL OR e.assignedTo.id = :assignedToId)
                      AND (:priority IS NULL OR e.priority = :priority)
                      AND (:q IS NULL OR :q = '' OR LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(COALESCE(e.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))
                    ORDER BY e.startAt ASC
                    """,
            countQuery = """
                    SELECT COUNT(e) FROM CalendarEvent e
                    WHERE (
                        (e.startAt >= :rangeStart AND e.startAt < :rangeEnd)
                        OR (e.endAt IS NOT NULL AND e.endAt >= :rangeStart AND e.startAt < :rangeEnd)
                    )
                      AND (:eventType IS NULL OR e.eventType = :eventType)
                      AND (:status IS NULL OR e.status = :status)
                      AND (:caseId IS NULL OR e.caseFile.id = :caseId)
                      AND (:clientId IS NULL OR e.client.id = :clientId
                           OR e.caseFile.client.id = :clientId)
                      AND (:assignedToId IS NULL OR e.assignedTo.id = :assignedToId)
                      AND (:priority IS NULL OR e.priority = :priority)
                      AND (:q IS NULL OR :q = '' OR LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(COALESCE(e.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))
                    """)
    Page<CalendarEvent> searchInRange(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("eventType") DomainEnums.CalendarEventType eventType,
            @Param("status") DomainEnums.CalendarEventStatus status,
            @Param("caseId") UUID caseId,
            @Param("clientId") UUID clientId,
            @Param("assignedToId") UUID assignedToId,
            @Param("priority") DomainEnums.TaskPriority priority,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            SELECT COUNT(e) FROM CalendarEvent e
            WHERE e.status = :status
              AND e.startAt >= :start AND e.startAt < :end
            """)
    long countByStatusBetween(
            @Param("status") DomainEnums.CalendarEventStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT COUNT(e) FROM CalendarEvent e
            WHERE e.eventType = :eventType
              AND e.status = :status
              AND e.startAt >= :start AND e.startAt < :end
            """)
    long countByTypeStatusBetween(
            @Param("eventType") DomainEnums.CalendarEventType eventType,
            @Param("status") DomainEnums.CalendarEventStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT COUNT(e) FROM CalendarEvent e
            WHERE e.status = :scheduled
              AND e.startAt < :now
              AND e.eventType IN (:task, :deadline)
            """)
    long countOverdue(
            @Param("scheduled") DomainEnums.CalendarEventStatus scheduled,
            @Param("now") LocalDateTime now,
            @Param("task") DomainEnums.CalendarEventType task,
            @Param("deadline") DomainEnums.CalendarEventType deadline);

    long countByCompany_IdAndStatus(
            UUID companyId, DomainEnums.CalendarEventStatus status);

    long countByCompany_Id(UUID companyId);
}
