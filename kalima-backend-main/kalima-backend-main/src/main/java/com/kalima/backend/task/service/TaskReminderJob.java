package com.kalima.backend.task.service;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskReminderJob {

    private static final Logger log = LoggerFactory.getLogger(TaskReminderJob.class);

    private final CompanyRepository companyRepository;
    private final TaskReminderService taskReminderService;

    @Value("${kalima.tasks.reminder-enabled:true}")
    private boolean enabled;

    public TaskReminderJob(CompanyRepository companyRepository,
                           TaskReminderService taskReminderService) {
        this.companyRepository = companyRepository;
        this.taskReminderService = taskReminderService;
    }

    @Scheduled(cron = "${kalima.tasks.reminder-cron:0 0 8 * * *}")
    public void runDaily() {
        if (!enabled) {
            log.debug("Task reminder job disabled — skipping");
            return;
        }
        for (Company company : companyRepository.findAll()) {
            try {
                TenantContext.set(company.getId());
                taskReminderService.processRemindersForCurrentTenant();
            } catch (Exception ex) {
                log.error("Task reminder job failed for company {}", company.getId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
