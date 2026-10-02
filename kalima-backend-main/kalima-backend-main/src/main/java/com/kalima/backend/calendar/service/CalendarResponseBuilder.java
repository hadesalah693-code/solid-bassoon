package com.kalima.backend.calendar.service;

import com.kalima.backend.calendar.dto.CalendarEventResponse;
import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.calendar.repository.CalendarEventReminderRepository;
import com.kalima.backend.calendar.support.CalendarEventMapper;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.domain.entity.Person;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CalendarResponseBuilder {

    private final CalendarEventReminderRepository reminderRepository;

    public CalendarResponseBuilder(CalendarEventReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    public CalendarEventResponse toResponse(CalendarEvent e) {
        CaseFile cf = e.getCaseFile();
        Dispute d = e.getDispute();
        Person client = e.getClient() != null ? e.getClient()
                : (cf != null ? cf.getClient() : null);
        Person assigned = e.getAssignedTo();
        Person createdBy = e.getCreatedBy();
        List<Integer> reminders = reminderRepository.findByCalendarEvent_Id(e.getId()).stream()
                .map(r -> r.getOffsetMinutes())
                .sorted()
                .toList();
        return new CalendarEventResponse(
                e.getId(),
                e.getTitle(),
                e.getDescription(),
                e.getEventType(),
                e.getSourceModule(),
                e.getSourceId(),
                cf != null ? cf.getId() : null,
                cf != null ? cf.getCaseNumber() : null,
                cf != null ? cf.getTitle() : null,
                client != null ? client.getId() : null,
                client != null ? personName(client) : null,
                e.getTaskItem() != null ? e.getTaskItem().getId() : null,
                d != null ? d.getId() : null,
                d != null ? d.getDisputeNumber() : null,
                e.getStartAt(),
                e.getEndAt(),
                e.isAllDay(),
                e.getStatus(),
                e.getPriority(),
                assigned != null ? assigned.getId() : null,
                assigned != null ? assigned.getFullName() : null,
                createdBy.getId(),
                createdBy.getFullName(),
                CalendarEventMapper.colorKey(e),
                !e.isProjection(),
                CalendarEventMapper.sourceHref(e),
                reminders,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private static String personName(Person p) {
        if (p.getFullName() != null && !p.getFullName().isBlank()) {
            return p.getFullName();
        }
        return p.getCompanyName();
    }
}
