package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.ReceiptCreateRequest;
import com.kalima.backend.accounting.dto.ReceiptResponse;
import com.kalima.backend.accounting.service.ReceiptService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/receipts")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')")
public class ReceiptController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final ReceiptService service;

    public ReceiptController(ReceiptService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public Page<ReceiptResponse> list(
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(clientId, from, to, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public ReceiptResponse getById(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ReceiptResponse> create(
            @Valid @RequestBody ReceiptCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        ReceiptResponse created = service.create(request);
        URI location = uriBuilder.path("/api/accounting/receipts/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
