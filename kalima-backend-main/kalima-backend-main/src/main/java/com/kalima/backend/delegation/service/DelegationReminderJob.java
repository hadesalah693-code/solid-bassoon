package com.kalima.backend.delegation.service;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DelegationReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DelegationReminderJob.class);

    private final CompanyRepository companyRepository;
    private final DelegationReminderService delegationReminderService;

    @Value("${kalima.delegations.reminder-enabled:true}")
    private boolean enabled;

    public DelegationReminderJob(CompanyRepository companyRepository,
                                 DelegationReminderService delegationReminderService) {
        this.companyRepository = companyRepository;
        this.delegationReminderService = delegationReminderService;
    }

    @Scheduled(cron = "${kalima.delegations.reminder-cron:0 0 8 * * *}")
    public void runDaily() {
        if (!enabled) {
            return;
        }
        for (Company company : companyRepository.findAll()) {
            try {
                TenantContext.set(company.getId());
                delegationReminderService.processRemindersForCurrentTenant();
            } catch (Exception ex) {
                log.error("Delegation reminder job failed for company {}", company.getId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
