package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.TimeBillingRequest;
import com.kalima.backend.accounting.dto.TimeEntryCreateRequest;
import com.kalima.backend.accounting.dto.TimeEntryResponse;
import com.kalima.backend.accounting.service.TimeBillingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Time tracking.
 *
 * <p>Recording time moves cost between deferred revenue and unbilled receivables
 * but recognises neither revenue nor a receivable from the client, so junior lawyers
 * can log their own hours. Voiding is restricted because it reverses a ledger entry.
 */
@RestController
@RequestMapping("/api/accounting/time-entries")
public class TimeEntryController {

    private static final String ROLES =
            "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER','LAWYER','ASSOCIATE')";
    private static final String WRITE_ROLES =
            "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER','LAWYER','ASSOCIATE')";
    private static final String VOID_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";

    private final TimeBillingService service;

    public TimeEntryController(TimeBillingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<TimeEntryResponse> list(
            @RequestParam(name = "caseId", required = false) UUID caseId,
            @RequestParam(name = "lawyerId", required = false) UUID lawyerId,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        return service.list(caseId, lawyerId, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public TimeEntryResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Unbilled work on a case: what a timesheet invoice could draw on. */
    @GetMapping("/case/{caseId}/summary")
    @PreAuthorize(ROLES)
    public TimeBillingService.CaseWorkSummary caseSummary(@PathVariable UUID caseId) {
        return service.summary(caseId);
    }

    /**
     * Turns the unbilled time on a case into an invoice.
     *
     * <p>Restricted to the write roles because it recognises revenue and clears the
     * unbilled receivable: it is a bookkeeping act, not a timesheet edit.
     */
    @PostMapping("/case/{caseId}/bill")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<TimeBillingService.BillingResult> bill(
            @PathVariable UUID caseId,
            @Valid @RequestBody TimeBillingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.bill(caseId, request));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<TimeEntryResponse> create(
            @Valid @RequestBody TimeEntryCreateRequest request) {
        TimeEntryResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(VOID_ROLES)
    public ResponseEntity<Void> voidEntry(@PathVariable UUID id) {
        service.voidEntry(id);
        return ResponseEntity.noContent().build();
    }
}