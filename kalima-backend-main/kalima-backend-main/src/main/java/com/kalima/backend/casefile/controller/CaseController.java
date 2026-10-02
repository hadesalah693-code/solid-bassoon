package com.kalima.backend.casefile.controller;

import com.kalima.backend.casefile.dto.CaseCreateRequest;
import com.kalima.backend.casefile.dto.CaseHistoryEntry;
import com.kalima.backend.casefile.dto.CaseInternalNoteCreateRequest;
import com.kalima.backend.casefile.dto.CaseInternalNoteResponse;
import com.kalima.backend.casefile.dto.CaseResponse;
import com.kalima.backend.casefile.dto.CaseStatusUpdateRequest;
import com.kalima.backend.casefile.dto.CaseUpdateRequest;
import com.kalima.backend.casefile.service.CaseService;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.casefile.service.CaseInternalNoteService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseService caseService;
    private final CaseAccessService caseAccessService;
    private final CurrentUserService currentUserService;
    private final CaseInternalNoteService caseInternalNoteService;

    public CaseController(
            CaseService caseService,
            CaseAccessService caseAccessService,
            CurrentUserService currentUserService,
            CaseInternalNoteService caseInternalNoteService) {
        this.caseService = caseService;
        this.caseAccessService = caseAccessService;
        this.currentUserService = currentUserService;
        this.caseInternalNoteService = caseInternalNoteService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<CaseResponse> create(@Valid @RequestBody CaseCreateRequest request,
                                               UriComponentsBuilder uriBuilder) {
        CaseResponse created = caseService.create(request);
        URI location = uriBuilder.path("/api/cases/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public Page<CaseResponse> list(
            @RequestParam(name = "status", required = false) DomainEnums.CaseStatus status,
            @RequestParam(name = "priority", required = false) DomainEnums.CasePriority priority,
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "assignedLawyerId", required = false) UUID assignedLawyerId,
            @RequestParam(name = "fromDate", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate fromDate,
            @RequestParam(name = "toDate", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate toDate,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "includeArchived", required = false, defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20) Pageable pageable) {
        return caseService.search(status, priority, clientId, assignedLawyerId,
            fromDate, toDate, search, includeArchived, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public CaseResponse getById(@PathVariable UUID id) {
        caseAccessService.requireReadableCase(id, currentUserService.requireCurrentPerson());
        return caseService.findById(id);
    }

    @GetMapping("/{id}/related-cases")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public Page<CaseResponse> relatedCases(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        caseAccessService.requireReadableCase(id, currentUserService.requireCurrentPerson());
        return caseService.relatedCases(id, pageable);
    }

    /**
     * Timeline of audit events for a single case.
     *
     * <p>Permission mirrors {@code GET /api/cases/{id}}: every role that
     * can read the case can read its history. Tenant isolation and CLIENT
     * ownership are enforced in {@link CaseService#history(UUID, Pageable)},
     * so this endpoint is safe to expose to non-admin roles without
     * weakening the global ADMIN-only {@code /api/audit-logs} feed.
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public Page<CaseHistoryEntry> history(
            @PathVariable UUID id,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        caseAccessService.requireReadableCase(id, currentUserService.requireCurrentPerson());
        return caseService.history(id, pageable);
    }

    @GetMapping("/{id}/internal-notes")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public Page<CaseInternalNoteResponse> internalNotes(
            @PathVariable UUID id,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        caseAccessService.requireReadableCase(id, currentUserService.requireCurrentPerson());
        return caseInternalNoteService.list(id, pageable);
    }

    @PostMapping("/{id}/internal-notes")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<CaseInternalNoteResponse> addInternalNote(
            @PathVariable UUID id,
            @Valid @RequestBody CaseInternalNoteCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        caseAccessService.requireReadableCase(id, currentUserService.requireCurrentPerson());
        CaseInternalNoteResponse created = caseInternalNoteService.create(id, request);
        URI location = uriBuilder.path("/api/cases/{id}/internal-notes")
            .buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public CaseResponse update(@PathVariable UUID id,
                               @Valid @RequestBody CaseUpdateRequest request) {
        caseAccessService.requireReadableCase(id, currentUserService.requireCurrentPerson());
        return caseService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER')")
    public CaseResponse changeStatus(@PathVariable UUID id,
                                     @Valid @RequestBody CaseStatusUpdateRequest request) {
        caseAccessService.requireReadableCase(id, currentUserService.requireCurrentPerson());
        return caseService.changeStatus(id, request);
    }

    /**
     * Archive is a soft-delete: sets {@code archived_at} and status=ARCHIVED.
     * We intentionally expose it as {@code DELETE} so clients that assume
     * REST conventions (HTTP DELETE = remove) get the expected behaviour
     * without ever losing data.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        caseService.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    public CaseResponse restore(@PathVariable UUID id) {
        return caseService.restore(id);
    }
}
