package com.kalima.backend.agent.night;

import com.kalima.backend.agent.night.dto.NightAgentReport;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/night")
public class NightAgentController {

    private final NightAgentService nightAgentService;

    public NightAgentController(NightAgentService nightAgentService) {
        this.nightAgentService = nightAgentService;
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public NightAgentReport latestReport() {
        return nightAgentService.latestReport();
    }

    /** Manual trigger for admins (scheduled job runs at 2 AM). */
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public NightAgentReport runNow() {
        return nightAgentService.runForCurrentTenant();
    }
}
