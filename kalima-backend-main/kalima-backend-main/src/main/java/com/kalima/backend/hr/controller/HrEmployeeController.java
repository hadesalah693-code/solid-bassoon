package com.kalima.backend.hr.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.hr.dto.HrEmployeeRequest;
import com.kalima.backend.hr.dto.HrEmployeeResponse;
import com.kalima.backend.hr.service.HrEmployeeService;
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
@RequestMapping("/api/hr/employees")
public class HrEmployeeController {

    private final HrEmployeeService service;

    public HrEmployeeController(HrEmployeeService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<HrEmployeeResponse> create(
            @Valid @RequestBody HrEmployeeRequest request,
            UriComponentsBuilder uriBuilder) {
        HrEmployeeResponse created = service.create(request);
        URI location = uriBuilder.path("/api/hr/employees/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public Page<HrEmployeeResponse> list(
            @RequestParam(name = "departmentId", required = false) UUID departmentId,
            @RequestParam(name = "positionId", required = false) UUID positionId,
            @RequestParam(name = "status", required = false) DomainEnums.HrEmployeeStatus status,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(departmentId, positionId, status, search, pageable);
    }

    /**
     * Punch IDs already used by an employee. The employee form shows these as a
     * hint so a clerk does not enter an ID that is already taken.
     */
    @GetMapping("/device-user-ids")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public List<String> deviceUserIds() {
        return service.listDeviceUserIds();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public HrEmployeeResponse get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public HrEmployeeResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody HrEmployeeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
