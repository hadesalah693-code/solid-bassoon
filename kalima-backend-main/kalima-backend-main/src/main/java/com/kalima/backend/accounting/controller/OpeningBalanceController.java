package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.OpeningBalanceRequest;
import com.kalima.backend.accounting.dto.OpeningBalanceResponse;
import com.kalima.backend.accounting.service.OpeningBalanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Opening balances are posted as a real balanced journal entry, which is what keeps
 * the ledger the single source of truth for every balance in the system.
 */
@RestController
@RequestMapping("/api/accounting/opening-balances")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class OpeningBalanceController {

    private final OpeningBalanceService service;

    public OpeningBalanceController(OpeningBalanceService service) {
        this.service = service;
    }

    /** Reports whether the one-shot opening entry has already been posted. */
    @GetMapping
    public Map<String, Boolean> status() {
        return Map.of("posted", service.hasOpeningEntry());
    }

    @PostMapping
    public ResponseEntity<OpeningBalanceResponse> post(
            @Valid @RequestBody OpeningBalanceRequest request,
            UriComponentsBuilder uriBuilder) {
        OpeningBalanceResponse created = service.post(request);
        URI location = uriBuilder.path("/api/accounting/journal/{id}")
                .buildAndExpand(created.journalEntryId()).toUri();
        return ResponseEntity.created(location).body(created);
    }
}
