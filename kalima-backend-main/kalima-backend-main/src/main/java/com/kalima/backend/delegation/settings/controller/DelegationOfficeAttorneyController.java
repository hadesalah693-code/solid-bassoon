package com.kalima.backend.delegation.settings.controller;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.delegation.settings.dto.DelegationAttorneyMatchResponse;
import com.kalima.backend.delegation.settings.dto.DelegationOfficeAttorneyCreateRequest;
import com.kalima.backend.delegation.settings.dto.DelegationOfficeAttorneyResponse;
import com.kalima.backend.delegation.settings.dto.DelegationOfficeAttorneyUpdateRequest;
import com.kalima.backend.delegation.settings.service.DelegationOfficeAttorneyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Delegation Settings — office attorneys management.
 *
 * <p>{@code GET} (list) is open to the same staff roles that can read
 * delegations — they need this list to fill the agent field on the
 * delegation form. {@code GET /auto} and {@code GET /match} are creation-flow
 * helpers, so they're scoped to the same roles allowed to create a delegation
 * ({@code DelegationController#create}). Mutating endpoints
 * ({@code POST}/{@code PATCH}/{@code DELETE}) are restricted to
 * {@code ADMIN}/{@code SENIOR_LAWYER} — the same "elevated, administrative
 * action" pairing used elsewhere for destructive/configuration operations
 * (e.g. {@code CaseController} archive/delete, {@code DelegationDocumentController} delete).
 */
@RestController
@RequestMapping("/api/delegations/settings/attorneys")
public class DelegationOfficeAttorneyController {

    private final DelegationOfficeAttorneyService service;

    public DelegationOfficeAttorneyController(DelegationOfficeAttorneyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DelegationOfficeAttorneyResponse> list(
            @RequestParam(name = "activeOnly", required = false, defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    /**
     * Resolves the agent that the delegation-creation form should auto-fill:
     * present only when exactly one active attorney is configured.
     */
    @GetMapping("/auto")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<PartySummary> autoAgent() {
        PartySummary resolved = service.resolveAutoAgent();
        return resolved == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(resolved);
    }

    @GetMapping("/match")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DelegationAttorneyMatchResponse match(@RequestParam("name") String name) {
        return service.matchByName(name);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    public ResponseEntity<DelegationOfficeAttorneyResponse> create(
            @Valid @RequestBody DelegationOfficeAttorneyCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        DelegationOfficeAttorneyResponse created = service.create(request);
        URI location = uriBuilder.path("/api/delegations/settings/attorneys/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    public DelegationOfficeAttorneyResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody DelegationOfficeAttorneyUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
