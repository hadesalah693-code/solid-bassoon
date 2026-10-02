package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.RecurringInvoiceCreateRequest;
import com.kalima.backend.accounting.dto.RecurringInvoiceResponse;
import com.kalima.backend.accounting.service.RecurringInvoiceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Recurring invoice templates.
 *
 * <p>Creating a template posts nothing, so senior lawyers may manage schedules;
 * marking a run as generated changes what the next invoice will be, so it is
 * limited to the accountant roles.
 */
@RestController
@RequestMapping("/api/accounting/recurring-invoices")
public class RecurringInvoiceController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String SCHEDULE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final RecurringInvoiceService service;

    public RecurringInvoiceController(RecurringInvoiceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<RecurringInvoiceResponse> list(
            @RequestParam(name = "clientId", required = false) UUID clientId) {
        return service.list(clientId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public RecurringInvoiceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Schedules due to run on or before a given day. */
    @GetMapping("/due")
    @PreAuthorize(ROLES)
    public List<RecurringInvoiceResponse> due(
            @RequestParam(name = "on", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return service.due(on);
    }

    @PostMapping
    @PreAuthorize(SCHEDULE_ROLES)
    public ResponseEntity<RecurringInvoiceResponse> create(
            @Valid @RequestBody RecurringInvoiceCreateRequest request) {
        RecurringInvoiceResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
 * Issues the invoice for the period that is due and advances the schedule.
     *
     * <p>The counterpart of {@code /generated}: that endpoint only moves the schedule
     * for a run whose invoice was raised by hand, this one raises the invoice and then
     * moves it. A run cannot be billed twice.
     */
    @PostMapping("/{id}/generate")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<RecurringInvoiceService.GenerationResult> generate(
            @PathVariable UUID id,
            @RequestParam(name = "runDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate runDate,
            @RequestParam(name = "dueDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(id, runDate, dueDate));
    }

    /** Advances the schedule; called only after the invoice for that run exists. */
    @PostMapping("/{id}/generated")
    @PreAuthorize(WRITE_ROLES)
    public RecurringInvoiceResponse markGenerated(
            @PathVariable UUID id,
            @RequestParam(name = "runDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate runDate) {
        return service.markGenerated(id, runDate);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SCHEDULE_ROLES)
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}