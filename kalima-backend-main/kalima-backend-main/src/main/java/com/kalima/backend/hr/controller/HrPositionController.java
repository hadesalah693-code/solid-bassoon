package com.kalima.backend.hr.controller;

import com.kalima.backend.hr.dto.HrPositionRequest;
import com.kalima.backend.hr.dto.HrPositionResponse;
import com.kalima.backend.hr.service.HrPositionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hr/positions")
public class HrPositionController {

    private final HrPositionService service;

    public HrPositionController(HrPositionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<HrPositionResponse> create(
            @Valid @RequestBody HrPositionRequest request,
            UriComponentsBuilder uriBuilder) {
        HrPositionResponse created = service.create(request);
        URI location = uriBuilder.path("/api/hr/positions/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public Page<HrPositionResponse> list(
            @RequestParam(name = "departmentId", required = false) UUID departmentId,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(departmentId, active, search, pageable);
    }

    /** Flat list for the position picker on the employee form. */
    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public List<HrPositionResponse> options() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public HrPositionResponse get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public HrPositionResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody HrPositionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
