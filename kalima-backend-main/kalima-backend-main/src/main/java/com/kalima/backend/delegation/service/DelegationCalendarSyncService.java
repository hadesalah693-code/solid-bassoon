package com.kalima.backend.delegation.service;

import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.calendar.repository.CalendarEventReminderRepository;
import com.kalima.backend.calendar.repository.CalendarEventRepository;
import com.kalima.backend.calendar.service.CalendarProjectionService;
import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.delegation.repository.DelegationRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class DelegationCalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(DelegationCalendarSyncService.class);

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventReminderRepository reminderRepository;
    private final CalendarProjectionService calendarProjectionService;
    private final DelegationRepository delegationRepository;

    public DelegationCalendarSyncService(
            CalendarEventRepository calendarEventRepository,
            CalendarEventReminderRepository reminderRepository,
            CalendarProjectionService calendarProjectionService,
            DelegationRepository delegationRepository) {
        this.calendarEventRepository = calendarEventRepository;
        this.reminderRepository = reminderRepository;
        this.calendarProjectionService = calendarProjectionService;
        this.delegationRepository = delegationRepository;
    }

    public void sync(Delegation delegation, Person actor) {
        if (delegation.getStatus() == DomainEnums.DelegationStatus.CANCELLED
                || delegation.getExpiresAt() == null) {
            // No expiry date means nothing to project on the calendar (or it was cleared).
            calendarProjectionService.removeProjection(
                    DomainEnums.CalendarSourceModule.DELEGATION, delegation.getId());
            return;
        }

        CalendarEvent event = calendarEventRepository
                .findByProjection(DomainEnums.CalendarSourceModule.DELEGATION, delegation.getId())
                .orElseGet(CalendarEvent::new);

        event.setCompany(delegation.getCompany());
        event.setSourceModule(DomainEnums.CalendarSourceModule.DELEGATION);
        event.setSourceId(delegation.getId());
        event.setEventType(DomainEnums.CalendarEventType.DOCUMENT_EXPIRY);
        event.setTitle("انتهاء توكيل — " + delegation.getDelegationNumber());
        event.setDescription(delegation.getTitle());
        event.setClient(delegation.getClient());
        event.setCaseFile(null);
        event.setDispute(null);
        event.setTaskItem(null);
        event.setAssignedTo(delegation.getAgent());
        event.setAllDay(true);
        LocalDateTime start = delegation.getExpiresAt().atStartOfDay();
        event.setStartAt(start);
        event.setEndAt(delegation.getExpiresAt().atTime(LocalTime.of(23, 59)));
        event.setPriority(DomainEnums.TaskPriority.MEDIUM);
        event.setStatus(deriveCalendarStatus(delegation));
        if (event.getCreatedBy() == null) {
            event.setCreatedBy(actor);
        }

        CalendarEvent saved = calendarEventRepository.save(event);
        replaceReminders(saved, delegation.getReminderOffsets());
    }

    /** Idempotent sync for all delegations in the current tenant (calendar backfill). */
    public int backfillCurrentTenant() {
        int count = 0;
        for (Delegation delegation : delegationRepository.findAll()) {
            Person actor = delegation.getAgent() != null
                    ? delegation.getAgent()
                    : delegation.getClient();
            sync(delegation, actor);
            count++;
        }
        log.info("Synced {} delegation expiry events to calendar for current tenant", count);
        return count;
    }

    private static DomainEnums.CalendarEventStatus deriveCalendarStatus(Delegation delegation) {
        if (delegation.getStatus() == DomainEnums.DelegationStatus.CANCELLED) {
            return DomainEnums.CalendarEventStatus.CANCELLED;
        }
        LocalDate today = LocalDate.now();
        if (delegation.getExpiresAt().isBefore(today)) {
            return DomainEnums.CalendarEventStatus.COMPLETED;
        }
        return DomainEnums.CalendarEventStatus.SCHEDULED;
    }

    private void replaceReminders(CalendarEvent event, Set<Integer> offsetDays) {
        reminderRepository.deleteByCalendarEvent_Id(event.getId());
        if (offsetDays == null || offsetDays.isEmpty()) {
            return;
        }
        List<Integer> minuteOffsets = new ArrayList<>();
        for (Integer days : offsetDays) {
            if (days != null && days > 0) {
                minuteOffsets.add(days * 1440);
            }
        }
        calendarProjectionService.ensureDefaultReminders(event, minuteOffsets);
    }
}
