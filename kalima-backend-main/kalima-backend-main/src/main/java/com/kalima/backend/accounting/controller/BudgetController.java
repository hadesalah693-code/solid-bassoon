package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.BudgetCreateRequest;
import com.kalima.backend.accounting.dto.BudgetResponse;
import com.kalima.backend.accounting.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Budgets.
 *
 * <p>Budgets never post, so setting one is not a cash movement and the read roles
 * can create them. Actual figures come from the ledger on every request.
 */
@RestController
@RequestMapping("/api/accounting/budgets")
public class BudgetController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<BudgetResponse> list(
            @RequestParam(name = "fiscalYear", required = false) Integer fiscalYear) {
        return service.list(fiscalYear);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public BudgetResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody BudgetCreateRequest request) {
        BudgetResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public BudgetResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody BudgetCreateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}