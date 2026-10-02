package com.kalima.backend.agent.night;

import com.kalima.backend.agent.config.AgentProperties;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NightAgentJob {

    private static final Logger log = LoggerFactory.getLogger(NightAgentJob.class);

    private final AgentProperties properties;
    private final CompanyRepository companyRepository;
    private final NightAgentService nightAgentService;

    public NightAgentJob(AgentProperties properties,
                         CompanyRepository companyRepository,
                         NightAgentService nightAgentService) {
        this.properties = properties;
        this.companyRepository = companyRepository;
        this.nightAgentService = nightAgentService;
    }

    @Scheduled(cron = "${kalima.agent.night-agent-cron:0 0 2 * * *}")
    public void runNightly() {
        if (!properties.isNightAgentEnabled()) {
            log.debug("Night agent disabled — skipping");
            return;
        }
        for (Company company : companyRepository.findAll()) {
            try {
                TenantContext.set(company.getId());
                nightAgentService.runForCurrentTenant();
            } catch (Exception ex) {
                log.error("Night agent failed for company {}", company.getId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
