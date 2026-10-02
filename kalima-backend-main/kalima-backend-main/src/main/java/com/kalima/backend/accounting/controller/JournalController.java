package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.JournalEntryCreateRequest;
import com.kalima.backend.accounting.dto.JournalEntryResponse;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.service.JournalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/accounting/journal")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')")
public class JournalController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final JournalService service;

    public JournalController(JournalService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public Page<JournalEntryResponse> list(
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "source", required = false) JournalEntry.Source source,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(from, to, source, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public JournalEntryResponse getById(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<JournalEntryResponse> create(
            @Valid @RequestBody JournalEntryCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        JournalEntryResponse created = service.create(request);
        URI location = uriBuilder.path("/api/accounting/journal/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize(WRITE_ROLES)
    public JournalEntryResponse reverse(@PathVariable UUID id) {
        return service.reverse(id);
    }
}
