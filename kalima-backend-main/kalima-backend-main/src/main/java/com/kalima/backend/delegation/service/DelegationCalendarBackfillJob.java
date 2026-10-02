package com.kalima.backend.delegation.service;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures existing delegations have calendar projections on startup (idempotent).
 */
@Component
public class DelegationCalendarBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(DelegationCalendarBackfillJob.class);

    private final CompanyRepository companyRepository;
    private final DelegationCalendarSyncService calendarSyncService;
    private final boolean enabled;

    public DelegationCalendarBackfillJob(
            CompanyRepository companyRepository,
            DelegationCalendarSyncService calendarSyncService,
            @Value("${kalima.delegations.calendar-backfill-enabled:true}") boolean enabled) {
        this.companyRepository = companyRepository;
        this.calendarSyncService = calendarSyncService;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            return;
        }
        int total = 0;
        for (Company company : companyRepository.findAll()) {
            TenantContext.set(company.getId());
            try {
                total += calendarSyncService.backfillCurrentTenant();
            } catch (RuntimeException ex) {
                log.warn("Delegation calendar backfill failed for company {}", company.getId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Delegation calendar backfill completed ({} delegation rows processed)", total);
    }
}
