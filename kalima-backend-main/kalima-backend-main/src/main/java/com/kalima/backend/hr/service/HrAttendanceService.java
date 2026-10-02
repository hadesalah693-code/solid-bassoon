package com.kalima.backend.hr.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.device.ZkAttendanceLog;
import com.kalima.backend.hr.device.ZkDeviceException;
import com.kalima.backend.hr.device.ZkDeviceUser;
import com.kalima.backend.hr.device.ZkPunchMapper;
import com.kalima.backend.hr.device.ZkTecoClient;
import com.kalima.backend.hr.dto.HrAttendanceDayRow;
import com.kalima.backend.hr.dto.HrAttendanceRecordResponse;
import com.kalima.backend.hr.dto.HrAttendanceSummaryResponse;
import com.kalima.backend.hr.dto.HrManualPunchRequest;
import com.kalima.backend.hr.dto.HrSyncResultResponse;
import com.kalima.backend.hr.entity.HrAttendanceDevice;
import com.kalima.backend.hr.entity.HrAttendanceRecord;
import com.kalima.backend.hr.entity.HrEmployee;
import com.kalima.backend.hr.repository.HrAttendanceRecordRepository;
import com.kalima.backend.hr.repository.HrAttendanceRecordSpecifications;
import com.kalima.backend.hr.repository.HrEmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class HrAttendanceService {

    private static final Logger log = LoggerFactory.getLogger(HrAttendanceService.class);

    /** Office defaults for the daily sheet; adjustable per tenant later. */
    private static final LocalTime START_OF_WORK = LocalTime.of(9, 0);
    private static final LocalTime END_OF_WORK = LocalTime.of(17, 0);
    private static final Duration LATE_GRACE = Duration.ofMinutes(15);

    private final HrAttendanceRecordRepository recordRepository;
    private final HrEmployeeRepository employeeRepository;
    private final HrAttendanceDeviceService deviceService;
    private final HrEmployeeService employeeService;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final ZkTecoClient zkTecoClient;
    private final TransactionTemplate transactionTemplate;

    public HrAttendanceService(HrAttendanceRecordRepository recordRepository,
                              HrEmployeeRepository employeeRepository,
                              HrAttendanceDeviceService deviceService,
                              HrEmployeeService employeeService,
                              CompanyRepository companyRepository,
                              CurrentUserService currentUserService,
                              AuditLogService auditLogService,
                              ZkTecoClient zkTecoClient,
                              PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.recordRepository = recordRepository;
        this.employeeRepository = employeeRepository;
        this.deviceService = deviceService;
        this.employeeService = employeeService;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.zkTecoClient = zkTecoClient;
    }

    // ------------------------------------------------------------------- reading

    @Transactional(readOnly = true)
    public Page<HrAttendanceRecordResponse> search(UUID deviceId,
                                                   UUID employeeId,
                                                   DomainEnums.HrPunchType punchType,
                                                   DomainEnums.HrVerifyMode verifyMode,
                                                   Boolean unmatchedOnly,
                                                   LocalDateTime from,
                                                   LocalDateTime to,
                                                   Pageable pageable) {
        return recordRepository.findAll(
                        HrAttendanceRecordSpecifications.filtered(
                                deviceId, employeeId, punchType, verifyMode, unmatchedOnly, from, to),
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public HrAttendanceRecordResponse findById(UUID id) {
        return toResponse(requireRecord(id));
    }

    /** Punches whose device punch ID has no employee yet. */
    @Transactional(readOnly = true)
    public List<HrAttendanceRecordResponse> listUnmatched(LocalDateTime from, LocalDateTime to) {
        return recordRepository.findAll(
                        HrAttendanceRecordSpecifications.filtered(
                                null, null, null, null, Boolean.TRUE, from, to),
                        org.springframework.data.domain.Pageable.unpaged())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUnmatched() {
        return recordRepository.countUnmatched();
    }

    /**
     * Daily sheet: one row per employee who has punches that day, plus the
     * headline counters. Rows are built in memory because the worked-time and
     * late/early flags depend on pairing punches across devices and shifts.
     */
    @Transactional(readOnly = true)
    public HrAttendanceSummaryResponse dailySheet(LocalDate date,
                                                 UUID departmentId,
                                                 DomainEnums.HrEmployeeStatus status) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = from.plusDays(1);

        List<HrAttendanceRecord> punches = recordRepository.findRange(from, to);
        Map<UUID, List<HrAttendanceRecord>> byEmployee = new LinkedHashMap<>();
        for (HrAttendanceRecord record : punches) {
            if (record.getEmployee() == null) {
                continue;
            }
            byEmployee.computeIfAbsent(record.getEmployee().getId(), k -> new ArrayList<>()).add(record);
        }

        List<HrAttendanceDayRow> rows = new ArrayList<>();
        int present = 0;
        int late = 0;
        int earlyLeave = 0;

        for (Map.Entry<UUID, List<HrAttendanceRecord>> entry : byEmployee.entrySet()) {
            List<HrAttendanceRecord> employeePunches = entry.getValue();
            employeePunches.sort(Comparator.comparing(HrAttendanceRecord::getPunchedAt));
            HrEmployee employee = employeePunches.get(0).getEmployee();

            if (departmentId != null
                    && (employee.getDepartment() == null
                    || !departmentId.equals(employee.getDepartment().getId()))) {
                continue;
            }
            if (status != null && employee.getStatus() != status) {
                continue;
            }

            HrAttendanceDayRow row = buildDayRow(day, employee, employeePunches);
            rows.add(row);
            present++;
            if (row.status() == DomainEnums.HrAttendanceDayStatus.LATE) {
                late++;
            }
            if (row.status() == DomainEnums.HrAttendanceDayStatus.EARLY_LEAVE) {
                earlyLeave++;
            }
        }

        rows.sort(Comparator
                .comparing(HrAttendanceDayRow::employeeName, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(HrAttendanceDayRow::employeeNumber, Comparator.nullsLast(String::compareToIgnoreCase)));

        int total = employeeRepository.listByStatus(DomainEnums.HrEmployeeStatus.ACTIVE).size();
        return new HrAttendanceSummaryResponse(day, total, present, total - present, late, earlyLeave,
                (int) recordRepository.countUnmatched(), rows);
    }

    // ------------------------------------------------------------------ writing

    /**
     * Pulls every punch from a device and stores the ones we do not have yet.
     *
     * <p>Devices cannot be queried by date, so the whole log is downloaded each
     * time and de-duplicated against {@code (device, punchId, timestamp)}. The
     * network read happens outside the write transaction to avoid holding a
     * database transaction open while a slow device streams its log.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public HrSyncResultResponse syncDevice(UUID deviceId) {
        Person actor = currentUserService.requireCurrentPerson();
        HrAttendanceDevice device = deviceService.requireDevice(deviceId);

        if (device.getProtocol() != DomainEnums.HrDeviceProtocol.ZK_TECO_TCP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "this device protocol is pushed by the device itself, not pulled");
        }

        List<ZkAttendanceLog> logs;
        try {
            logs = zkTecoClient.fetchAttendance(device.getIpAddress(), device.getPort());
        } catch (ZkDeviceException e) {
            transactionTemplate.executeWithoutResult(status -> {
                HrAttendanceDevice managed = deviceService.requireDevice(deviceId);
                deviceService.markSyncFailed(managed, e.getMessage());
            });
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }

        HrSyncResultResponse result = transactionTemplate.execute(status -> {
            HrAttendanceDevice managed = deviceService.requireDevice(deviceId);
            LocalDateTime highWaterMark = recordRepository.findLastPunchedAt(deviceId);
            SyncOutcome outcome = persistPunches(managed, logs, highWaterMark);

            String message = String.format(Locale.ROOT,
                    "تمت مزامنة %d حركة: %d جديدة، %d مكررة، %d بدون موظف",
                    outcome.fetched(), outcome.inserted(), outcome.skipped(), outcome.unmatched());
            deviceService.markSynced(managed, message);

            return new HrSyncResultResponse(deviceId, managed.getName(), outcome.fetched(), outcome.inserted(),
                    outcome.skipped(), outcome.unmatched(), outcome.maxPunchedAt(), message);
        });

        auditLogService.recordSuccess(
                AuditAction.HR_ATTENDANCE_SYNCED,
                actor,
                "HrAttendanceDevice",
                deviceId,
                Map.of("fetched", result.fetched(),
                        "inserted", result.inserted(),
                        "skipped", result.skipped(),
                        "unmatched", result.unmatched()));

        return result;
    }

    /** Punch IDs currently enrolled on a device, so the UI can suggest them. */
    @Transactional(readOnly = true)
    public List<ZkDeviceUser> listDeviceUsers(UUID deviceId) {
        HrAttendanceDevice device = deviceService.requireDevice(deviceId);
        if (device.getProtocol() != DomainEnums.HrDeviceProtocol.ZK_TECO_TCP) {
            return List.of();
        }
        try {
            return zkTecoClient.fetchUsers(device.getIpAddress(), device.getPort());
        } catch (ZkDeviceException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    /**
     * Stores a punch typed in by hand, for terminals that cannot be reached over
     * the network. It goes through the same matching and de-duplication path as
     * a pulled punch, so a later sync will not duplicate it.
     */
    public HrAttendanceRecordResponse addManualPunch(HrManualPunchRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        HrAttendanceDevice device = deviceService.requireDevice(request.deviceId());
        String punchId = HrEmployeeService.trimToNull(request.deviceUserId());
        if (punchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deviceUserId is required");
        }

        if (recordRepository.countExistingPunch(device.getId(), punchId, request.punchedAt()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this punch is already recorded");
        }

        HrAttendanceRecord record = new HrAttendanceRecord();
        record.setCompany(currentTenantReference());
        record.setDevice(device);
        record.setDeviceUserId(punchId);
        record.setPunchedAt(request.punchedAt());
        record.setPunchType(request.punchType() == null
                ? DomainEnums.HrPunchType.UNKNOWN
                : request.punchType());
        record.setVerifyMode(request.verifyMode() == null
                ? DomainEnums.HrVerifyMode.UNKNOWN
                : request.verifyMode());
        record.setWorkCode(HrEmployeeService.trimToNull(request.workCode()));
        record.setRawPayload(null);
        record.setEmployee(employeeRepository.findByDeviceUserId(punchId).orElse(null));

        HrAttendanceRecord saved = recordRepository.save(record);
        auditLogService.recordSuccess(
                AuditAction.HR_ATTENDANCE_IMPORTED,
                actor,
                "HrAttendanceRecord",
                saved.getId(),
                Map.of("deviceUserId", punchId, "manual", true));
        return toResponse(saved);
    }

    public void deleteRecord(UUID id) {
        Person actor = currentUserService.requireCurrentPerson();
        HrAttendanceRecord record = requireRecord(id);
        recordRepository.delete(record);
        auditLogService.recordSuccess(
                AuditAction.HR_ATTENDANCE_DELETED,
                actor,
                "HrAttendanceRecord",
                id,
                Map.of("deviceUserId", record.getDeviceUserId()));
    }

    /**
     * Re-runs the punch-ID → employee match for every unmatched punch, for when
     * an employee was fixed after the punches were already imported.
     */
    public int rematchUnmatched() {
        Person actor = currentUserService.requireCurrentPerson();
        List<HrAttendanceRecord> unmatched = recordRepository.findUnmatchedByDeviceUserIds(
                employeeService.listDeviceUserIds());
        int linked = 0;
        for (HrAttendanceRecord record : unmatched) {
            Optional<HrEmployee> employee = employeeRepository.findByDeviceUserId(record.getDeviceUserId());
            if (employee.isPresent()) {
                record.setEmployee(employee.get());
                linked++;
            }
        }
        if (linked > 0) {
            recordRepository.saveAll(unmatched);
            auditLogService.recordSuccess(
                    AuditAction.HR_ATTENDANCE_IMPORTED,
                    actor,
                    "HrAttendanceRecord",
                    null,
                    Map.of("relinked", linked));
        }
        return linked;
    }

    HrAttendanceRecord requireRecord(UUID id) {
        return recordRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "attendance record not found"));
    }

    // ------------------------------------------------------------------ internals

    private record SyncOutcome(int fetched, int inserted, int skipped, int unmatched, LocalDateTime maxPunchedAt) {
    }

    private SyncOutcome persistPunches(HrAttendanceDevice device,
                                       List<ZkAttendanceLog> logs,
                                       LocalDateTime highWaterMark) {
        int inserted = 0;
        int skipped = 0;
        int unmatched = 0;
        LocalDateTime maxPunchedAt = null;

        // Cache the employee lookup: a full log is thousands of punches but only
        // a few dozen employees, and this runs once per punch otherwise.
        Map<String, Optional<HrEmployee>> employeeCache = new LinkedHashMap<>();

        for (ZkAttendanceLog punch : logs) {
            String punchId = HrEmployeeService.trimToNull(punch.deviceUserId());
            if (punchId == null || punch.punchedAt() == null) {
                continue;
            }
            if (punch.punchedAt().isAfter(maxPunchedAt)) {
                maxPunchedAt = punch.punchedAt();
            }
            if (highWaterMark != null && !punch.punchedAt().isAfter(highWaterMark)) {
                // Already covered by a previous sync; the device sends its whole log.
                skipped++;
                continue;
            }
            if (recordRepository.countExistingPunch(device.getId(), punchId, punch.punchedAt()) > 0) {
                skipped++;
                continue;
            }

            Optional<HrEmployee> employee = employeeCache.computeIfAbsent(
                    punchId.toLowerCase(Locale.ROOT),
                    key -> employeeRepository.findByDeviceUserId(punchId));
            if (employee.isEmpty()) {
                unmatched++;
            }

            try {
                HrAttendanceRecord record = new HrAttendanceRecord();
                record.setCompany(device.getCompany());
                record.setDevice(device);
                record.setDeviceUserId(punchId);
                record.setPunchedAt(punch.punchedAt());
                record.setPunchType(ZkPunchMapper.toPunchType(punch.stateCode()));
                record.setVerifyMode(ZkPunchMapper.toVerifyMode(punch.verifyCode()));
                record.setWorkCode(punch.workCode() == 0 ? null : String.valueOf(punch.workCode()));
                record.setRawPayload(punch.raw());
                record.setEmployee(employee.orElse(null));
                recordRepository.save(record);
                inserted++;
            } catch (DataIntegrityViolationException e) {
                // Two syncs of the same device can race; the unique index is the
                // real guarantee, so a losing insert is skipped, not fatal.
                log.debug("Duplicate punch ignored for device {} punchId {} at {}",
                        device.getId(), punchId, punch.punchedAt());
                skipped++;
            }
        }

        if (inserted == 0 && skipped == 0) {
            log.info("Device {} returned no usable punches", device.getName());
        }
        return new SyncOutcome(logs.size(), inserted, skipped, unmatched, maxPunchedAt);
    }

    private HrAttendanceDayRow buildDayRow(LocalDate day, HrEmployee employee, List<HrAttendanceRecord> punches) {
        LocalDateTime first = punches.get(0).getPunchedAt();
        LocalDateTime last = punches.get(punches.size() - 1).getPunchedAt();
        long workedMinutes = Duration.between(first, last).toMinutes();

        boolean hasIn = punches.stream()
                .anyMatch(p -> p.getPunchType() == DomainEnums.HrPunchType.CHECK_IN
                        || p.getPunchType() == DomainEnums.HrPunchType.BREAK_IN
                        || p.getPunchType() == DomainEnums.HrPunchType.OVERTIME_IN);
        boolean hasOut = punches.stream()
                .anyMatch(p -> p.getPunchType() == DomainEnums.HrPunchType.CHECK_OUT
                        || p.getPunchType() == DomainEnums.HrPunchType.BREAK_OUT
                        || p.getPunchType() == DomainEnums.HrPunchType.OVERTIME_OUT);

        DomainEnums.HrAttendanceDayStatus dayStatus;
        if (!hasIn) {
            dayStatus = DomainEnums.HrAttendanceDayStatus.ABSENT;
        } else if (first.toLocalTime().isAfter(START_OF_WORK.plus(LATE_GRACE))) {
            dayStatus = DomainEnums.HrAttendanceDayStatus.LATE;
        } else if (!hasOut || last.toLocalTime().isBefore(END_OF_WORK)) {
            dayStatus = DomainEnums.HrAttendanceDayStatus.EARLY_LEAVE;
        } else {
            dayStatus = DomainEnums.HrAttendanceDayStatus.PRESENT;
        }

        return new HrAttendanceDayRow(
                day,
                employee.getId(),
                employee.getEmployeeNumber(),
                employee.getFullName(),
                employee.getDepartment() != null ? employee.getDepartment().getName() : null,
                employee.getPosition() != null ? employee.getPosition().getTitle() : null,
                employee.getStatus(),
                first,
                last,
                workedMinutes,
                dayStatus,
                punches.stream().map(this::toResponse).toList());
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    HrAttendanceRecordResponse toResponse(HrAttendanceRecord r) {
        HrEmployee employee = r.getEmployee();
        return new HrAttendanceRecordResponse(
                r.getId(),
                r.getDevice() != null ? r.getDevice().getId() : null,
                r.getDevice() != null ? r.getDevice().getName() : null,
                employee != null ? employee.getId() : null,
                employee != null ? employee.getFullName() : null,
                employee != null ? employee.getEmployeeNumber() : null,
                r.getDeviceUserId(),
                r.getPunchedAt(),
                r.getPunchType(),
                r.getVerifyMode(),
                r.getWorkCode());
    }
}
