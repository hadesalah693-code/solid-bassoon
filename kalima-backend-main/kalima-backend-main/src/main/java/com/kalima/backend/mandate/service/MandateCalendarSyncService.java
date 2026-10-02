package com.kalima.backend.mandate.service;

import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.calendar.repository.CalendarEventRepository;
import com.kalima.backend.calendar.repository.CalendarEventReminderRepository;
import com.kalima.backend.calendar.service.CalendarProjectionService;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.mandate.entity.Mandate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MandateCalendarSyncService {

    private static final List<Integer> REMINDERS = List.of(1440);

    private final CalendarEventRepository eventRepository;
    private final CalendarProjectionService projectionService;
    private final CalendarEventReminderRepository reminderRepository;

    public MandateCalendarSyncService(
            CalendarEventRepository eventRepository,
            CalendarProjectionService projectionService,
            CalendarEventReminderRepository reminderRepository) {
        this.eventRepository = eventRepository;
        this.projectionService = projectionService;
        this.reminderRepository = reminderRepository;
    }

    public void sync(Mandate mandate) {
        if (mandate.getSessionDate() == null || mandate.getDeletedAt() != null) {
            remove(mandate.getId());
            return;
        }
        CalendarEvent event = eventRepository.findByProjection(
                DomainEnums.CalendarSourceModule.MANDATE, mandate.getId())
                .orElseGet(CalendarEvent::new);
        event.setCompany(mandate.getCompany());
        event.setSourceModule(DomainEnums.CalendarSourceModule.MANDATE);
        event.setSourceId(mandate.getId());
        event.setEventType(DomainEnums.CalendarEventType.MANDATE);
        event.setTitle("إنابة — " + mandate.getMandateNumber());
        event.setDescription(mandate.getReason());
        event.setCaseFile(mandate.getCaseFile());
        event.setClient(mandate.getClient());
        event.setTaskItem(null);
        event.setDispute(null);
        java.time.LocalDateTime eventDate = mandate.getNextSessionDate() != null
            ? mandate.getNextSessionDate() : mandate.getSessionDate();
        event.setStartAt(eventDate);
        event.setEndAt(eventDate.plusHours(1));
        event.setAllDay(false);
        event.setAssignedTo(mandate.getSubstitutedLawyer());
        event.setStatus(mandate.getNextSessionDate() != null
            ? DomainEnums.CalendarEventStatus.SCHEDULED : switch (mandate.getStatus()) {
            case COMPLETED -> DomainEnums.CalendarEventStatus.COMPLETED;
            case CANCELLED -> DomainEnums.CalendarEventStatus.CANCELLED;
            case PENDING, CONFIRMED, IN_PROGRESS -> DomainEnums.CalendarEventStatus.SCHEDULED;
        });
        event.setCreatedBy(mandate.getCreatedBy());
        CalendarEvent saved = eventRepository.save(event);
        if (mandate.getStatus() == DomainEnums.MandateStatus.PENDING
                || mandate.getStatus() == DomainEnums.MandateStatus.CONFIRMED) {
            projectionService.ensureDefaultReminders(saved, REMINDERS);
        } else {
            reminderRepository.deleteByCalendarEvent_Id(saved.getId());
        }
    }

    public void remove(UUID mandateId) {
        projectionService.removeProjection(DomainEnums.CalendarSourceModule.MANDATE, mandateId);
    }
}