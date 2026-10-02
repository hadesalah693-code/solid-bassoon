package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.BankAccountCreateRequest;
import com.kalima.backend.accounting.dto.BankAccountResponse;
import com.kalima.backend.accounting.dto.BankTransferRequest;
import com.kalima.backend.accounting.dto.BankTransferResponse;
import com.kalima.backend.accounting.service.BankAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/bank-accounts")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')")
public class BankAccountController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final BankAccountService service;

    public BankAccountController(BankAccountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<BankAccountResponse> list() {
        return service.list();
    }

    @GetMapping("/transfers")
    @PreAuthorize(ROLES)
    public List<BankTransferResponse> transfers() {
        return service.transfers();
    }

    @PostMapping("/transfers")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<BankTransferResponse> transfer(
            @Valid @RequestBody BankTransferRequest request,
            UriComponentsBuilder uriBuilder) {
        BankTransferResponse created = service.transfer(request);
        URI location = uriBuilder.path("/api/accounting/bank-accounts/transfers/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public BankAccountResponse getById(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<BankAccountResponse> create(
            @Valid @RequestBody BankAccountCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        BankAccountResponse created = service.create(request);
        URI location = uriBuilder.path("/api/accounting/bank-accounts/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public BankAccountResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody BankAccountCreateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize(WRITE_ROLES)
    public BankAccountResponse setActive(@PathVariable UUID id,
                                         @RequestParam(name = "active") boolean active) {
        return service.setActive(id, active);
    }
}
