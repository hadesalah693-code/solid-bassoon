package com.kalima.backend.calendar.service;

import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.calendar.entity.CalendarEventReminder;
import com.kalima.backend.calendar.repository.CalendarEventReminderRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CalendarReminderService {

    private static final Logger log = LoggerFactory.getLogger(CalendarReminderService.class);
    /** Catch up reminders missed within this window (e.g. downtime). */
    private static final int CATCH_UP_HOURS = 24;
    private static final int MAX_OFFSET_MINUTES = 10_080;

    private final CalendarEventReminderRepository reminderRepository;
    private final NotificationService notificationService;

    public CalendarReminderService(
            CalendarEventReminderRepository reminderRepository,
            NotificationService notificationService) {
        this.reminderRepository = reminderRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public int processDueRemindersForCurrentTenant() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusHours(CATCH_UP_HOURS);
        LocalDateTime rangeStart = windowStart;
        LocalDateTime rangeEnd = now.plusMinutes(MAX_OFFSET_MINUTES);

        List<CalendarEventReminder> pending = reminderRepository.findPendingReminders(
                DomainEnums.CalendarEventStatus.SCHEDULED,
                rangeStart,
                rangeEnd);

        int sent = 0;
        for (CalendarEventReminder reminder : pending) {
            CalendarEvent event = reminder.getCalendarEvent();
            if (event == null || event.getStartAt() == null) {
                continue;
            }
            LocalDateTime fireAt = event.getStartAt().minusMinutes(reminder.getOffsetMinutes());
            if (fireAt.isAfter(now) || fireAt.isBefore(windowStart)) {
                continue;
            }
            Person recipient = event.getAssignedTo() != null
                    ? event.getAssignedTo()
                    : event.getCreatedBy();
            if (recipient == null) {
                reminder.setNotifiedAt(now);
                continue;
            }
            notificationService.notifyCalendarReminder(
                    event, recipient, reminder.getOffsetMinutes());
            reminder.setNotifiedAt(now);
            sent++;
        }
        if (sent > 0) {
            log.info("Calendar reminders sent: {}", sent);
        }
        return sent;
    }
}
