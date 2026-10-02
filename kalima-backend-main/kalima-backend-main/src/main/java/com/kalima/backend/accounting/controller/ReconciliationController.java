package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.BankReconciliationCreateRequest;
import com.kalima.backend.accounting.dto.BankReconciliationResponse;
import com.kalima.backend.accounting.dto.StatementLineCreateRequest;
import com.kalima.backend.accounting.dto.StatementLineResponse;
import com.kalima.backend.accounting.entity.BankStatementLine;
import com.kalima.backend.accounting.service.ReconciliationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Bank reconciliation.
 *
 * <p>Matching posts nothing to the ledger, but completing a reconciliation is what
 * asserts the account is trustworthy, so it is limited to the accountant roles.
 */
@RestController
@RequestMapping("/api/accounting/reconciliations")
public class ReconciliationController {

    private static final String READ_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<BankReconciliationResponse> open(
            @Valid @RequestBody BankReconciliationCreateRequest request) {
        BankReconciliationResponse created = service.open(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<BankReconciliationResponse> list(
            @RequestParam(name = "bankAccountId", required = false) UUID bankAccountId) {
        return service.list(bankAccountId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public BankReconciliationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<StatementLineResponse> addLine(
            @PathVariable UUID id,
            @Valid @RequestBody StatementLineCreateRequest request) {
        StatementLineResponse created = service.addLine(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}/lines")
    @PreAuthorize(READ_ROLES)
    public List<StatementLineResponse> listLines(
            @PathVariable UUID id,
            @RequestParam(name = "onlyUnmatched", defaultValue = "false") boolean onlyUnmatched) {
        return service.listLines(id, onlyUnmatched);
    }

    /** Candidate matches for one line: same account, day and amount. */
    @GetMapping("/lines/{lineId}/suggestions")
    @PreAuthorize(READ_ROLES)
    public List<ReconciliationService.MatchSuggestion> suggest(@PathVariable UUID lineId) {
        return service.suggest(lineId);
    }

    @PatchMapping("/lines/{lineId}/match")
    @PreAuthorize(WRITE_ROLES)
    public StatementLineResponse match(
            @PathVariable UUID lineId,
            @RequestParam BankStatementLine.MatchType type,
            @RequestParam UUID matchedId) {
        return service.match(lineId, type, matchedId);
    }

    @PatchMapping("/lines/{lineId}/unmatch")
    @PreAuthorize(WRITE_ROLES)
    public StatementLineResponse unmatch(@PathVariable UUID lineId) {
        return service.unmatch(lineId);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(WRITE_ROLES)
    public BankReconciliationResponse complete(@PathVariable UUID id) {
        return service.complete(id);
    }
}