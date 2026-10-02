package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.CreditDebitNoteCreateRequest;
import com.kalima.backend.accounting.dto.CreditDebitNoteResponse;
import com.kalima.backend.accounting.entity.CreditDebitNote;
import com.kalima.backend.accounting.service.CreditDebitNoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Credit and debit notes against client invoices.
 *
 * <p>Issuing a note always posts to the ledger, so this is restricted to roles that
 * can already move money. Voiding is a reversal rather than a delete, which keeps
 * the original document and its entry visible to anyone auditing later.
 */
@RestController
@RequestMapping("/api/accounting/credit-debit-notes")
public class CreditDebitNoteController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final CreditDebitNoteService service;

    public CreditDebitNoteController(CreditDebitNoteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<CreditDebitNoteResponse> list(
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "noteType", required = false) CreditDebitNote.NoteType noteType,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        return service.list(clientId, noteType, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public CreditDebitNoteResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<CreditDebitNoteResponse> create(
            @Valid @RequestBody CreditDebitNoteCreateRequest request) {
        CreditDebitNoteResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> voidNote(@PathVariable UUID id) {
        service.voidNote(id);
        return ResponseEntity.noContent().build();
    }
}