package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.RefundCreateRequest;
import com.kalima.backend.accounting.dto.RefundResponse;
import com.kalima.backend.accounting.service.RefundService;
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
 * Refunds paid back to clients. Money leaves the firm here, so reads are open to the
 * same roles as receipts but writes are restricted like every other cash movement.
 */
@RestController
@RequestMapping("/api/accounting/refunds")
public class RefundController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final RefundService service;

    public RefundController(RefundService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<RefundResponse> list(
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "invoiceId", required = false) UUID invoiceId,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        return service.list(clientId, invoiceId, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public RefundResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<RefundResponse> create(@Valid @RequestBody RefundCreateRequest request) {
        RefundResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> voidRefund(@PathVariable UUID id) {
        service.voidRefund(id);
        return ResponseEntity.noContent().build();
    }
}