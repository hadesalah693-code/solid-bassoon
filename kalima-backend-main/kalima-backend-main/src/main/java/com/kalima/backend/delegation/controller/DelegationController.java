package com.kalima.backend.delegation.controller;

import com.kalima.backend.audit.dto.AuditLogResponse;
import com.kalima.backend.audit.service.AuditLogQueryService;
import com.kalima.backend.delegation.dto.DelegationCaseLinkResponse;
import com.kalima.backend.delegation.dto.DelegationCreateRequest;
import com.kalima.backend.delegation.dto.DelegationListItemResponse;
import com.kalima.backend.delegation.dto.DelegationResponse;
import com.kalima.backend.delegation.dto.DelegationUpdateRequest;
import com.kalima.backend.delegation.dto.LinkDelegationCaseRequest;
import com.kalima.backend.delegation.service.DelegationCaseLinkService;
import com.kalima.backend.delegation.service.DelegationService;
import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/delegations")
public class DelegationController {

    private final DelegationService delegationService;
    private final DelegationCaseLinkService caseLinkService;
    private final AuditLogQueryService auditLogQueryService;

    public DelegationController(DelegationService delegationService,
                                DelegationCaseLinkService caseLinkService,
                                AuditLogQueryService auditLogQueryService) {
        this.delegationService = delegationService;
        this.caseLinkService = caseLinkService;
        this.auditLogQueryService = auditLogQueryService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DelegationResponse> create(@Valid @RequestBody DelegationCreateRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        DelegationResponse created = delegationService.create(request);
        URI location = uriBuilder.path("/api/delegations/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<DelegationListItemResponse> list(
            @RequestParam(name = "status", required = false) DomainEnums.DelegationStatus status,
            @RequestParam(name = "type", required = false) DomainEnums.DelegationType type,
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "agentId", required = false) UUID agentId,
            @RequestParam(name = "issuingCountry", required = false) String issuingCountry,
            @RequestParam(name = "starredOnly", required = false) Boolean starredOnly,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20)
            Pageable pageable) {
        return delegationService.search(status, type, clientId, agentId, issuingCountry, starredOnly, search, pageable);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public ResponseEntity<byte[]> export(
            @RequestParam(name = "status", required = false) DomainEnums.DelegationStatus status,
            @RequestParam(name = "type", required = false) DomainEnums.DelegationType type,
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "agentId", required = false) UUID agentId,
            @RequestParam(name = "issuingCountry", required = false) String issuingCountry,
            @RequestParam(name = "starredOnly", required = false) Boolean starredOnly,
            @RequestParam(name = "search", required = false) String search) {
        byte[] csv = delegationService.exportCsv(
                status, type, clientId, agentId, issuingCountry, starredOnly, search);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"delegations.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public DelegationResponse getById(@PathVariable UUID id) {
        return delegationService.getById(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DelegationResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody DelegationUpdateRequest request) {
        return delegationService.update(id, request);
    }

    @GetMapping("/{id}/cases")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DelegationCaseLinkResponse> listLinkedCases(@PathVariable UUID id) {
        return caseLinkService.listLinkedCases(id);
    }

    @PostMapping("/{id}/cases")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DelegationCaseLinkResponse> linkCase(
            @PathVariable UUID id,
            @Valid @RequestBody LinkDelegationCaseRequest request,
            UriComponentsBuilder uriBuilder) {
        DelegationCaseLinkResponse created = caseLinkService.linkCase(id, request);
        URI location = uriBuilder.path("/api/delegations/{id}/cases")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}/cases/{caseId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkCase(@PathVariable UUID id, @PathVariable UUID caseId) {
        caseLinkService.unlinkCase(id, caseId);
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<AuditLogResponse> activity(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        delegationService.getById(id);
        return auditLogQueryService.search(
                null, null, "Delegation", id, null, null, null, null, pageable);
    }
}
