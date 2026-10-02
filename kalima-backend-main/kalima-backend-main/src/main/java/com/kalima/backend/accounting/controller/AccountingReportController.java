package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.AccountingReportResponse;
import com.kalima.backend.accounting.dto.AccountLedgerResponse;
import com.kalima.backend.accounting.dto.AccountingReportResponse.ClientStatementResponse;
import com.kalima.backend.accounting.service.AccountingReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/reports")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')")
public class AccountingReportController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";

    private final AccountingReportService service;

    public AccountingReportController(AccountingReportService service) {
        this.service = service;
    }

    /**
     * One call backing the accounting dashboard: P&L, balance sheet, trial
     * balance, cash, AR aging and VAT in a single payload.
     */
    @GetMapping("/overview")
    @PreAuthorize(ROLES)
    public AccountingReportResponse overview(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }

    @GetMapping("/clients/{clientId}/statement")
    @PreAuthorize(ROLES)
    public ClientStatementResponse clientStatement(
            @PathVariable UUID clientId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.clientStatement(clientId, from, to);
    }

    @GetMapping("/accounts/{accountId}/ledger")
    @PreAuthorize(ROLES)
    public AccountLedgerResponse accountLedger(
            @PathVariable UUID accountId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.accountLedger(accountId, from, to, pageable);
    }
}
