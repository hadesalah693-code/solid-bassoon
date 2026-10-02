package com.kalima.backend.task.service;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.notification.service.NotificationService;
import com.kalima.backend.task.entity.TaskItem;
import com.kalima.backend.task.repository.TaskItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskReminderService {

    private static final Logger log = LoggerFactory.getLogger(TaskReminderService.class);

    private final TaskItemRepository taskItemRepository;
    private final NotificationService notificationService;

    public TaskReminderService(TaskItemRepository taskItemRepository,
                               NotificationService notificationService) {
        this.taskItemRepository = taskItemRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public int processRemindersForCurrentTenant() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime tomorrowEnd = today.plusDays(2).atStartOfDay();

        List<TaskItem> dueTomorrow = taskItemRepository.findTasksDueTomorrow(
                DomainEnums.TaskStatus.TODO,
                DomainEnums.TaskStatus.IN_PROGRESS,
                tomorrowStart,
                tomorrowEnd);
        int sent = 0;
        for (TaskItem task : dueTomorrow) {
            notificationService.notifyTaskDueSoon(task);
            task.setDueSoonNotifiedAt(LocalDateTime.now());
            sent++;
        }

        List<TaskItem> overdue = taskItemRepository.findOverdueTasksNeedingReminder(
                DomainEnums.TaskStatus.TODO,
                DomainEnums.TaskStatus.IN_PROGRESS,
                startOfToday);
        for (TaskItem task : overdue) {
            notificationService.notifyTaskOverdue(task);
            task.setOverdueNotifiedAt(LocalDateTime.now());
            sent++;
        }

        if (sent > 0) {
            log.info("Task due reminders sent for tenant: {}", sent);
        }
        return sent;
    }
}
