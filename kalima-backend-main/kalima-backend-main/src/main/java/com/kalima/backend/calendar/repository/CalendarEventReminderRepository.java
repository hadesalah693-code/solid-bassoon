package com.kalima.backend.calendar.repository;

import com.kalima.backend.calendar.entity.CalendarEventReminder;
import com.kalima.backend.domain.entity.DomainEnums;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CalendarEventReminderRepository extends JpaRepository<CalendarEventReminder, UUID> {

    List<CalendarEventReminder> findByCalendarEvent_Id(UUID calendarEventId);

    void deleteByCalendarEvent_Id(UUID calendarEventId);

    @EntityGraph(attributePaths = {
            "calendarEvent", "calendarEvent.caseFile", "calendarEvent.assignedTo",
            "calendarEvent.client", "calendarEvent.createdBy"
    })
    @Query("""
            SELECT r FROM CalendarEventReminder r
            JOIN r.calendarEvent e
            WHERE e.status = :scheduled
              AND r.notifiedAt IS NULL
              AND e.startAt >= :rangeStart
              AND e.startAt <= :rangeEnd
            """)
    List<CalendarEventReminder> findPendingReminders(
            @Param("scheduled") DomainEnums.CalendarEventStatus scheduled,
            @Param("rangeStart") java.time.LocalDateTime rangeStart,
            @Param("rangeEnd") java.time.LocalDateTime rangeEnd);
}
