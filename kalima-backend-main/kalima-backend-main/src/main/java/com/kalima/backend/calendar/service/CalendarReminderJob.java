package com.kalima.backend.calendar.service;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CalendarReminderJob {

    private static final Logger log = LoggerFactory.getLogger(CalendarReminderJob.class);

    private final CompanyRepository companyRepository;
    private final CalendarReminderService calendarReminderService;

    @Value("${kalima.calendar.reminder-enabled:true}")
    private boolean enabled;

    public CalendarReminderJob(CompanyRepository companyRepository,
                               CalendarReminderService calendarReminderService) {
        this.companyRepository = companyRepository;
        this.calendarReminderService = calendarReminderService;
    }

    @Scheduled(cron = "${kalima.calendar.reminder-cron:0 */15 * * * *}")
    public void runReminders() {
        if (!enabled) {
            return;
        }
        for (Company company : companyRepository.findAll()) {
            try {
                TenantContext.set(company.getId());
                calendarReminderService.processDueRemindersForCurrentTenant();
            } catch (Exception ex) {
                log.error("Calendar reminder job failed for company {}", company.getId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
