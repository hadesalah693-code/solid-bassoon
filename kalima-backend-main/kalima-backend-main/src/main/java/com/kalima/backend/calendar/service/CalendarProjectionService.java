package com.kalima.backend.calendar.service;

import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.calendar.entity.CalendarEventReminder;
import com.kalima.backend.calendar.repository.CalendarEventReminderRepository;
import com.kalima.backend.calendar.repository.CalendarEventRepository;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.hearing.repository.HearingRepository;
import com.kalima.backend.task.entity.TaskItem;
import com.kalima.backend.task.repository.TaskItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CalendarProjectionService {

    private static final Logger log = LoggerFactory.getLogger(CalendarProjectionService.class);
    private static final List<Integer> DEFAULT_TASK_REMINDERS = List.of(1440);
    private static final List<Integer> DEFAULT_HEARING_REMINDERS = List.of(1440);

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventReminderRepository reminderRepository;
    private final HearingRepository hearingRepository;
    private final TaskItemRepository taskItemRepository;

    public CalendarProjectionService(CalendarEventRepository calendarEventRepository,
                                     CalendarEventReminderRepository reminderRepository,
                                     HearingRepository hearingRepository,
                                     TaskItemRepository taskItemRepository) {
        this.calendarEventRepository = calendarEventRepository;
        this.reminderRepository = reminderRepository;
        this.hearingRepository = hearingRepository;
        this.taskItemRepository = taskItemRepository;
    }

    /**
     * Idempotent tenant backfill — must run inside a service transaction so the
     * Hibernate tenant filter is active.
     */
    public int backfillCurrentTenantIfEmpty() {
        UUID companyId = TenantContext.getRequired();

        if (calendarEventRepository.countByCompany_Id(companyId) > 0) {
            return 0;
        }

        int hearings = 0;
        int tasks = 0;

        for (Hearing hearing : hearingRepository.findAll()) {
            syncHearing(hearing);
            hearings++;
        }

        for (TaskItem task : taskItemRepository.findAll()) {
            syncTask(task);
            tasks++;
        }

        if (hearings > 0 || tasks > 0) {
            log.info("Calendar backfill tenant {}: {} hearings, {} tasks",
                    companyId, hearings, tasks);
        }

        return hearings + tasks;
    }

    public void syncHearing(Hearing hearing) {
        CalendarEvent event = calendarEventRepository
                .findByProjection(DomainEnums.CalendarSourceModule.HEARING, hearing.getId())
                .orElseGet(CalendarEvent::new);

        CaseFile caseFile = hearing.getCaseFile();
        event.setCompany(hearing.getCompany());
        event.setSourceModule(DomainEnums.CalendarSourceModule.HEARING);
        event.setSourceId(hearing.getId());
        event.setEventType(DomainEnums.CalendarEventType.HEARING);
        event.setTitle("جلسة — " + caseFile.getCaseNumber() + " — " + hearing.getCourtName());
        event.setDescription(hearing.getNotes());
        event.setCaseFile(caseFile);
        event.setClient(caseFile.getClient());
        event.setTaskItem(null);
        event.setDispute(null);
        event.setStartAt(hearing.getHearingDate());
        event.setEndAt(hearing.getHearingDate().plusHours(1));
        event.setAllDay(false);
        event.setStatus(deriveHearingStatus(hearing));
        event.setPriority(null);
        event.setAssignedTo(caseFile.getAssignedLawyer());
        if (event.getCreatedBy() == null) {
            event.setCreatedBy(hearing.getCreatedBy());
        }
        CalendarEvent saved = calendarEventRepository.save(event);
        if (hearing.getStatus() == com.kalima.backend.hearing.entity.HearingStatus.SCHEDULED) {
            ensureDefaultReminders(saved, DEFAULT_HEARING_REMINDERS);
        } else {
            reminderRepository.deleteByCalendarEvent_Id(saved.getId());
        }
    }

    public void syncTask(TaskItem task) {
        if (task.getDueDate() == null
                || task.getStatus() == DomainEnums.TaskStatus.CANCELLED) {
            removeProjection(DomainEnums.CalendarSourceModule.TASK, task.getId());
            return;
        }

        CalendarEvent event = calendarEventRepository
                .findByProjection(DomainEnums.CalendarSourceModule.TASK, task.getId())
                .orElseGet(CalendarEvent::new);

        event.setCompany(task.getCompany());
        event.setSourceModule(DomainEnums.CalendarSourceModule.TASK);
        event.setSourceId(task.getId());
        event.setEventType(DomainEnums.CalendarEventType.TASK);
        event.setTitle(task.getTitle());
        event.setDescription(task.getDescription());
        event.setCaseFile(task.getCaseFile());
        event.setDispute(task.getDispute());
        event.setTaskItem(task);
        event.setClient(task.getCaseFile() != null ? task.getCaseFile().getClient() : null);
        event.setStartAt(task.getDueDate());
        event.setEndAt(task.getDueDate().plusHours(1));
        event.setAllDay(false);
        event.setStatus(mapTaskStatus(task.getStatus()));
        event.setPriority(task.getPriority());
        event.setAssignedTo(task.getAssignedTo());
        if (event.getCreatedBy() == null) {
            event.setCreatedBy(task.getCreatedBy());
        }
        CalendarEvent saved = calendarEventRepository.save(event);
        ensureDefaultReminders(saved, DEFAULT_TASK_REMINDERS);
    }

    public void removeProjection(DomainEnums.CalendarSourceModule module, UUID sourceId) {
        calendarEventRepository.findByProjection(module, sourceId).ifPresent(event -> {
            reminderRepository.deleteByCalendarEvent_Id(event.getId());
            calendarEventRepository.delete(event);
        });
    }

    public void removeHearingsForCase(UUID caseId) {
        for (Hearing hearing : hearingRepository.findAllByCaseId(caseId)) {
            removeProjection(DomainEnums.CalendarSourceModule.HEARING, hearing.getId());
        }
    }

    public void syncHearingsForCase(UUID caseId) {
        for (Hearing hearing : hearingRepository.findAllByCaseId(caseId)) {
            syncHearing(hearing);
        }
    }

    private static DomainEnums.CalendarEventStatus deriveHearingStatus(Hearing hearing) {
        return switch (hearing.getStatus()) {
            case HELD -> DomainEnums.CalendarEventStatus.COMPLETED;
            case CANCELLED -> DomainEnums.CalendarEventStatus.CANCELLED;
            case SCHEDULED, POSTPONED, ADJOURNED -> DomainEnums.CalendarEventStatus.SCHEDULED;
        };
    }

    private static DomainEnums.CalendarEventStatus mapTaskStatus(DomainEnums.TaskStatus status) {
        if (status == DomainEnums.TaskStatus.DONE) {
            return DomainEnums.CalendarEventStatus.COMPLETED;
        }
        if (status == DomainEnums.TaskStatus.CANCELLED) {
            return DomainEnums.CalendarEventStatus.CANCELLED;
        }
        return DomainEnums.CalendarEventStatus.SCHEDULED;
    }

    public void ensureDefaultReminders(CalendarEvent event, List<Integer> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            return;
        }
        List<CalendarEventReminder> existing =
                reminderRepository.findByCalendarEvent_Id(event.getId());
        for (Integer offset : offsets) {
            boolean has = existing.stream()
                    .anyMatch(r -> r.getOffsetMinutes() == offset);
            if (!has) {
                CalendarEventReminder r = new CalendarEventReminder();
                r.setCompany(event.getCompany());
                r.setCalendarEvent(event);
                r.setOffsetMinutes(offset);
                reminderRepository.save(r);
            }
        }
    }
}