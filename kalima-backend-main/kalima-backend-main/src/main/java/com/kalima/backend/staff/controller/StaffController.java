package com.kalima.backend.staff.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.staff.dto.ChangePasswordRequest;
import com.kalima.backend.staff.dto.ResetPasswordRequest;
import com.kalima.backend.staff.dto.StaffCreateRequest;
import com.kalima.backend.staff.dto.StaffResponse;
import com.kalima.backend.staff.dto.StaffUpdateRequest;
import com.kalima.backend.staff.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffResponse> create(@Valid @RequestBody StaffCreateRequest request,
                                                UriComponentsBuilder uriBuilder) {
        StaffResponse created = staffService.create(request);
        URI location = uriBuilder.path("/api/staff/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY')")
    public Page<StaffResponse> list(
            @RequestParam(name = "role", required = false) DomainEnums.SystemRole role,
            @PageableDefault(size = 20) Pageable pageable) {
        return staffService.findAll(role, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY')")
    public StaffResponse getById(@PathVariable UUID id) {
        return staffService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public StaffResponse update(@PathVariable UUID id,
                                @Valid @RequestBody StaffUpdateRequest request) {
        return staffService.update(id, request);
    }

    /** Soft-disable (sets {@code is_active = false}). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable UUID id) {
        staffService.disable(id);
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@PathVariable UUID id) {
        staffService.enable(id);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id,
                              @Valid @RequestBody ResetPasswordRequest request) {
        staffService.resetPassword(id, request);
    }

    @PostMapping("/me/change-password")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request) {
        staffService.changeOwnPassword(request);
    }
}
