package com.kalima.backend.hr.controller;

import com.kalima.backend.hr.dto.HrDepartmentRequest;
import com.kalima.backend.hr.dto.HrDepartmentResponse;
import com.kalima.backend.hr.service.HrDepartmentService;
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
@RequestMapping("/api/hr/departments")
public class HrDepartmentController {

    private final HrDepartmentService service;

    public HrDepartmentController(HrDepartmentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<HrDepartmentResponse> create(
            @Valid @RequestBody HrDepartmentRequest request,
            UriComponentsBuilder uriBuilder) {
        HrDepartmentResponse created = service.create(request);
        URI location = uriBuilder.path("/api/hr/departments/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public Page<HrDepartmentResponse> list(
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(active, search, pageable);
    }

    /** Flat list for the department picker on the employee form. */
    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public List<HrDepartmentResponse> options() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public HrDepartmentResponse get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public HrDepartmentResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody HrDepartmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
