package com.kalima.backend.dashboard.controller;

import com.kalima.backend.dashboard.dto.DashboardSummaryResponse;
import com.kalima.backend.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only, tenant-scoped aggregates to power dashboards without N+1
 * per-case fetches. Count rules mirror {@code GET /api/cases} list semantics
 * (non-archived parent cases, active case statuses).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public DashboardSummaryResponse summary() {
        return dashboardService.summary();
    }
}
