package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.ExpenseCreateRequest;
import com.kalima.backend.accounting.dto.ExpenseResponse;
import com.kalima.backend.accounting.entity.Expense;
import com.kalima.backend.accounting.service.ExpenseService;
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
@RequestMapping("/api/accounting/expenses")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')")
public class ExpenseController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public Page<ExpenseResponse> list(
            @RequestParam(name = "category", required = false) Expense.Category category,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(category, from, to, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public ExpenseResponse getById(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ExpenseResponse> create(
            @Valid @RequestBody ExpenseCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        ExpenseResponse created = service.create(request);
        URI location = uriBuilder.path("/api/accounting/expenses/{id}")
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
