package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.AccountCreateRequest;
import com.kalima.backend.accounting.dto.AccountResponse;
import com.kalima.backend.accounting.dto.AccountUpdateRequest;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.service.ChartOfAccountsSeeder;
import com.kalima.backend.accounting.service.ChartOfAccountsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/accounts")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')")
public class AccountController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final ChartOfAccountsService service;
    private final ChartOfAccountsSeeder seeder;

    public AccountController(ChartOfAccountsService service, ChartOfAccountsSeeder seeder) {
        this.service = service;
        this.seeder = seeder;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<AccountResponse> list(@RequestParam(name = "type", required = false) Account.Type type) {
        return type == null ? service.list() : service.listByType(type);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody AccountCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        AccountResponse created = service.create(request);
        URI location = uriBuilder.path("/api/accounting/accounts").build().toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public AccountResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody AccountUpdateRequest request) {
        return service.update(id, request);
    }

    /**
     * Installs the system chart of accounts for the current company. Idempotent, and
     * also invoked automatically before the first posting, so this endpoint only needs
     * to be called to back-fill a company that existed before the seeder shipped.
     */
    @PostMapping("/seed")
    @PreAuthorize(WRITE_ROLES)
    public Map<String, Object> seed() {
        int inserted = seeder.ensureCurrentTenantSeeded();
        return Map.of("inserted", inserted,
                "message", inserted == 0
                        ? "دليل الحسابات مكتمل"
                        : "تمت إضافة " + inserted + " حساب نظامي");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        service.archive(id);
        return ResponseEntity.noContent().build();
    }
}
