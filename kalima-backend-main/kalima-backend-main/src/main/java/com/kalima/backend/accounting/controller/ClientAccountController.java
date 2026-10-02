package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.ClientAccountRequest;
import com.kalima.backend.accounting.dto.ClientAccountResponse;
import com.kalima.backend.accounting.service.ClientAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/client-accounts")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')")
public class ClientAccountController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final ClientAccountService service;

    public ClientAccountController(ClientAccountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<ClientAccountResponse> list(@RequestParam(name = "search", required = false) String search) {
        return service.list(search);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public ClientAccountResponse getById(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public ClientAccountResponse create(@RequestParam UUID clientId,
                                         @Valid @RequestBody ClientAccountRequest request) {
        return service.create(clientId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ClientAccountResponse update(@PathVariable UUID id,
                                         @Valid @RequestBody ClientAccountRequest request) {
        return service.update(id, request);
    }
}
