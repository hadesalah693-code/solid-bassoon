package com.kalima.backend.hr.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.hr.dto.HrAttendanceDayRow;
import com.kalima.backend.hr.dto.HrAttendanceRecordResponse;
import com.kalima.backend.hr.dto.HrAttendanceSummaryResponse;
import com.kalima.backend.hr.dto.HrManualPunchRequest;
import com.kalima.backend.hr.service.HrAttendanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/hr/attendance")
public class HrAttendanceController {

    private final HrAttendanceService service;

    public HrAttendanceController(HrAttendanceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public Page<HrAttendanceRecordResponse> list(
            @RequestParam(name = "deviceId", required = false) UUID deviceId,
            @RequestParam(name = "employeeId", required = false) UUID employeeId,
            @RequestParam(name = "punchType", required = false) DomainEnums.HrPunchType punchType,
            @RequestParam(name = "verifyMode", required = false) DomainEnums.HrVerifyMode verifyMode,
            @RequestParam(name = "unmatchedOnly", required = false) Boolean unmatchedOnly,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(deviceId, employeeId, punchType, verifyMode, unmatchedOnly, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public HrAttendanceRecordResponse get(@PathVariable UUID id) {
        return service.findById(id);
    }

    /** Daily sheet with worked times and the late/early counters. */
    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public HrAttendanceSummaryResponse daily(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "departmentId", required = false) UUID departmentId,
            @RequestParam(name = "status", required = false) DomainEnums.HrEmployeeStatus status) {
        return service.dailySheet(date, departmentId, status);
    }

    /** Punches waiting for their punch ID to be matched to an employee. */
    @GetMapping("/unmatched")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SECRETARY')")
    public List<HrAttendanceRecordResponse> unmatched(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return service.listUnmatched(from, to);
    }

    /** Re-runs the punch-ID → employee match for all unmatched punches. */
    @PostMapping("/rematch")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Integer> rematch() {
        return Map.of("linked", service.rematchUnmatched());
    }

    /** Punch typed in by hand, for terminals that cannot be reached over the network. */
    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<HrAttendanceRecordResponse> manual(
            @Valid @RequestBody HrManualPunchRequest request,
            UriComponentsBuilder uriBuilder) {
        HrAttendanceRecordResponse created = service.addManualPunch(request);
        URI location = uriBuilder.path("/api/hr/attendance/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public void delete(@PathVariable UUID id) {
        service.deleteRecord(id);
    }
}
