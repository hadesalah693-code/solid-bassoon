package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.FinancialPeriodRequest;
import com.kalima.backend.accounting.dto.FinancialPeriodResponse;
import com.kalima.backend.accounting.service.FinancialPeriodService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/periods")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class FinancialPeriodController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final FinancialPeriodService service;

    public FinancialPeriodController(FinancialPeriodService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<FinancialPeriodResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize(ROLES)
    public ResponseEntity<FinancialPeriodResponse> create(
            @Valid @RequestBody FinancialPeriodRequest request,
            UriComponentsBuilder uriBuilder) {
        FinancialPeriodResponse created = service.create(request);
        URI location = uriBuilder.path("/api/accounting/periods/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(ROLES)
    public FinancialPeriodResponse close(@PathVariable UUID id) {
        return service.close(id);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(ROLES)
    public FinancialPeriodResponse reopen(@PathVariable UUID id) {
        return service.reopen(id);
    }
}
