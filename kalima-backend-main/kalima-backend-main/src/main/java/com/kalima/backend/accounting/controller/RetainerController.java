package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.RetainerApplicationCreateRequest;
import com.kalima.backend.accounting.dto.RetainerApplicationResponse;
import com.kalima.backend.accounting.dto.RetainerCreateRequest;
import com.kalima.backend.accounting.dto.RetainerResponse;
import com.kalima.backend.accounting.entity.ClientRetainer;
import com.kalima.backend.accounting.service.RetainerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/retainers")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')")
public class RetainerController {

    private final RetainerService retainerService;

    public RetainerController(RetainerService retainerService) {
        this.retainerService = retainerService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<RetainerResponse> create(@Valid @RequestBody RetainerCreateRequest req) {
        return ResponseEntity.ok(retainerService.createRetainer(req));
    }

    @GetMapping
    public ResponseEntity<List<RetainerResponse>> list(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) ClientRetainer.RetainerStatus status) {
        return ResponseEntity.ok(retainerService.listRetainers(clientId, status));
    }

    @PostMapping("/{retainerId}/apply")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<RetainerApplicationResponse> apply(
            @PathVariable UUID retainerId,
            @Valid @RequestBody RetainerApplicationCreateRequest req) {
        if (!retainerId.equals(req.retainerId())) {
            throw new IllegalArgumentException("retainerId in path and body must match");
        }
        return ResponseEntity.ok(retainerService.applyRetainer(req));
    }

    @GetMapping("/{retainerId}/applications")
    public ResponseEntity<List<RetainerApplicationResponse>> applications(@PathVariable UUID retainerId) {
        return ResponseEntity.ok(retainerService.listApplications(retainerId));
    }
}