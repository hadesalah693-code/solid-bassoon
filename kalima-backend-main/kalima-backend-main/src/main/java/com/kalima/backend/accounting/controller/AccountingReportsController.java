package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.service.AccountingDashboardService;
import com.kalima.backend.accounting.service.CaseProfitabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Management reporting: portfolio profitability and the dashboard snapshot.
 *
 * <p>Read-only. Both endpoints aggregate the ledger rather than stored figures, so
 * there is no write path here to protect.
 */
@RestController
@RequestMapping("/api/accounting/reports")
public class AccountingReportsController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";

    private final CaseProfitabilityService profitabilityService;
    private final AccountingDashboardService dashboardService;

    public AccountingReportsController(CaseProfitabilityService profitabilityService,
                                       AccountingDashboardService dashboardService) {
        this.profitabilityService = profitabilityService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/case-profitability")
    @PreAuthorize(ROLES)
    public List<CaseProfitabilityService.CaseProfitability> caseProfitability(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return profitabilityService.report(from, to);
    }

    @GetMapping("/case-profitability/{caseId}")
    @PreAuthorize(ROLES)
    public CaseProfitabilityService.CaseProfitability caseProfitabilityDetail(
            @PathVariable("caseId") UUID caseId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return profitabilityService.forCase(caseId, from, to);
    }

    @GetMapping("/profitability-summary")
    @PreAuthorize(ROLES)
    public CaseProfitabilityService.PortfolioSummary profitabilitySummary(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return profitabilityService.summary(from, to);
    }

    @GetMapping("/dashboard")
    @PreAuthorize(ROLES)
    public AccountingDashboardService.DashboardSnapshot dashboard(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.snapshot(from, to);
    }
}