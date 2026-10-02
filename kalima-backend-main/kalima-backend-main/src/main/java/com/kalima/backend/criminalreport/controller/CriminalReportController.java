package com.kalima.backend.criminalreport.controller;

import com.kalima.backend.criminalreport.dto.CriminalReportCreateRequest;
import com.kalima.backend.criminalreport.dto.CriminalReportListItemResponse;
import com.kalima.backend.criminalreport.dto.CriminalReportResponse;
import com.kalima.backend.criminalreport.dto.CriminalReportStatusUpdateRequest;
import com.kalima.backend.criminalreport.dto.CriminalReportUpdateRequest;
import com.kalima.backend.criminalreport.service.CriminalReportService;
import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/criminal-reports")
public class CriminalReportController {

    private final CriminalReportService service;

    public CriminalReportController(CriminalReportService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<CriminalReportResponse> create(
            @Valid @RequestBody CriminalReportCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        CriminalReportResponse created = service.create(request);
        URI location = uriBuilder.path("/api/criminal-reports/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<CriminalReportListItemResponse> list(
            @RequestParam(name = "status", required = false) DomainEnums.CriminalReportStatus status,
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "assignedLawyerId", required = false) UUID assignedLawyerId,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(status, clientId, assignedLawyerId, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public CriminalReportResponse get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    @ResponseStatus(HttpStatus.OK)
    public CriminalReportResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody CriminalReportUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER')")
    @ResponseStatus(HttpStatus.OK)
    public CriminalReportResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CriminalReportStatusUpdateRequest request) {
        return service.updateStatus(id, request);
    }
}
