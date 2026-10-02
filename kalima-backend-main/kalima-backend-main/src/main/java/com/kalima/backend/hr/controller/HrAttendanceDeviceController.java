package com.kalima.backend.hr.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.hr.device.ZkDeviceUser;
import com.kalima.backend.hr.dto.HrAttendanceDeviceRequest;
import com.kalima.backend.hr.dto.HrAttendanceDeviceResponse;
import com.kalima.backend.hr.dto.HrDeviceTestResponse;
import com.kalima.backend.hr.dto.HrSyncResultResponse;
import com.kalima.backend.hr.service.HrAttendanceDeviceService;
import com.kalima.backend.hr.service.HrAttendanceService;
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
@RequestMapping("/api/hr/attendance-devices")
public class HrAttendanceDeviceController {

    private final HrAttendanceDeviceService service;
    private final HrAttendanceService attendanceService;

    public HrAttendanceDeviceController(HrAttendanceDeviceService service,
                                        HrAttendanceService attendanceService) {
        this.service = service;
        this.attendanceService = attendanceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<HrAttendanceDeviceResponse> create(
            @Valid @RequestBody HrAttendanceDeviceRequest request,
            UriComponentsBuilder uriBuilder) {
        HrAttendanceDeviceResponse created = service.create(request);
        URI location = uriBuilder.path("/api/hr/attendance-devices/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public Page<HrAttendanceDeviceResponse> list(
            @RequestParam(name = "protocol", required = false) DomainEnums.HrDeviceProtocol protocol,
            @RequestParam(name = "status", required = false) DomainEnums.HrDeviceStatus status,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(protocol, status, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public HrAttendanceDeviceResponse get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public HrAttendanceDeviceResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody HrAttendanceDeviceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Connectivity check for a saved device. */
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public HrDeviceTestResponse test(@PathVariable UUID id) {
        return service.testConnection(id);
    }

    /** Connectivity check for a device being added, before it is saved. */
    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public HrDeviceTestResponse testUnsaved(
            @RequestParam(name = "ipAddress") String ipAddress,
            @RequestParam(name = "port", required = false) Integer port) {
        return service.testUnsaved(ipAddress, port);
    }

    /** Pulls every punch the device currently holds and stores the new ones. */
    @PostMapping("/{id}/sync")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public HrSyncResultResponse sync(@PathVariable UUID id) {
        return attendanceService.syncDevice(id);
    }

    /** Punch IDs enrolled on the device, to reconcile against the employee list. */
    @GetMapping("/{id}/users")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public List<ZkDeviceUser> deviceUsers(@PathVariable UUID id) {
        return attendanceService.listDeviceUsers(id);
    }
}
