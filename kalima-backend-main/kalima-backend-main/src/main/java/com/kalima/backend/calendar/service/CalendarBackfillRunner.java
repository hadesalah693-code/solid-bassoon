package com.kalima.backend.calendar.service;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CalendarBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(CalendarBackfillRunner.class);

    private final CompanyRepository companyRepository;
    private final CalendarProjectionService projectionService;

    public CalendarBackfillRunner(
            CompanyRepository companyRepository,
            CalendarProjectionService projectionService) {
        this.companyRepository = companyRepository;
        this.projectionService = projectionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        for (Company company : companyRepository.findAll()) {
            try {
                TenantContext.set(company.getId());
                projectionService.backfillCurrentTenantIfEmpty();
            } catch (Exception ex) {
                log.error("Calendar backfill failed for company {}", company.getId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
