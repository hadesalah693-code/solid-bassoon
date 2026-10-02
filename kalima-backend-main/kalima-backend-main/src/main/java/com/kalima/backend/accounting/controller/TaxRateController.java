package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.TaxRateRequest;
import com.kalima.backend.accounting.dto.TaxRateResponse;
import com.kalima.backend.accounting.service.TaxRateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/tax-rates")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class TaxRateController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final TaxRateService service;

    public TaxRateController(TaxRateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<TaxRateResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize(ROLES)
    public ResponseEntity<TaxRateResponse> create(
            @Valid @RequestBody TaxRateRequest request,
            UriComponentsBuilder uriBuilder) {
        TaxRateResponse created = service.create(request);
        URI location = uriBuilder.path("/api/accounting/tax-rates/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(ROLES)
    public TaxRateResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody TaxRateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ROLES)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
