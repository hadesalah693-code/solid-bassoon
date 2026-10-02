package com.kalima.backend.hr.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.dto.HrEmployeeRequest;
import com.kalima.backend.hr.dto.HrEmployeeResponse;
import com.kalima.backend.hr.entity.HrEmployee;
import com.kalima.backend.hr.repository.HrAttendanceRecordRepository;
import com.kalima.backend.hr.repository.HrEmployeeRepository;
import com.kalima.backend.hr.repository.HrEmployeeSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class HrEmployeeService {

    private final HrEmployeeRepository repository;
    private final HrDepartmentService departmentService;
    private final HrPositionService positionService;
    private final HrAttendanceRecordRepository attendanceRepository;
    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public HrEmployeeService(HrEmployeeRepository repository,
                             HrDepartmentService departmentService,
                             HrPositionService positionService,
                             HrAttendanceRecordRepository attendanceRepository,
                             CompanyRepository companyRepository,
                             PersonRepository personRepository,
                             CurrentUserService currentUserService,
                             AuditLogService auditLogService) {
        this.repository = repository;
        this.departmentService = departmentService;
        this.positionService = positionService;
        this.attendanceRepository = attendanceRepository;
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<HrEmployeeResponse> search(UUID departmentId,
                                           UUID positionId,
                                           DomainEnums.HrEmployeeStatus status,
                                           String search,
                                           Pageable pageable) {
        String normalized = trimToNull(search);
        return repository.findAll(
                        HrEmployeeSpecifications.filtered(departmentId, positionId, status, normalized),
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public HrEmployeeResponse findById(UUID id) {
        return toResponse(requireEmployee(id));
    }

    /** Punch IDs already registered by an employee — used to pre-fill the sync screen. */
    @Transactional(readOnly = true)
    public List<String> listDeviceUserIds() {
        return repository.listDeviceUserIds();
    }

    public HrEmployeeResponse create(HrEmployeeRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        String employeeNumber = requireText(request.employeeNumber(), "employeeNumber");
        UUID tenantId = TenantContext.getRequired();
        if (repository.existsByCompany_IdAndEmployeeNumber(tenantId, employeeNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "employee number already exists");
        }
        String deviceUserId = trimToNull(request.deviceUserId());
        if (deviceUserId != null && repository.existsByCompany_IdAndDeviceUserId(tenantId, deviceUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "device punch id is already linked to another employee");
        }

        HrEmployee entity = new HrEmployee();
        entity.setCompany(currentTenantReference());
        entity.setEmployeeNumber(employeeNumber);
        applyRequest(entity, request, deviceUserId);
        entity.setStatus(request.status() == null ? DomainEnums.HrEmployeeStatus.ACTIVE : request.status());
        entity.setTerminatedAt(entity.getStatus() == DomainEnums.HrEmployeeStatus.TERMINATED
                ? LocalDateTime.now()
                : null);

        HrEmployee saved = repository.save(entity);
        // Punches may already have arrived from a device before the punch ID was
        // registered here, so claim them now instead of leaving them unmatched.
        int linked = linkUnmatchedPunches(saved);
        auditLogService.recordSuccess(
                AuditAction.HR_EMPLOYEE_CREATED,
                actor,
                "HrEmployee",
                saved.getId(),
                Map.of("employeeNumber", saved.getEmployeeNumber(),
                        "linkedPunches", linked));
        return toResponse(saved);
    }

    public HrEmployeeResponse update(UUID id, HrEmployeeRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        HrEmployee entity = requireEmployee(id);

        if (request.employeeNumber() != null) {
            String employeeNumber = requireText(request.employeeNumber(), "employeeNumber");
            if (repository.existsByCompany_IdAndEmployeeNumberAndIdNot(
                    TenantContext.getRequired(), employeeNumber, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "employee number already exists");
            }
            entity.setEmployeeNumber(employeeNumber);
        }

        String deviceUserId = null;
        if (request.deviceUserId() != null) {
            deviceUserId = trimToNull(request.deviceUserId());
            if (deviceUserId != null && repository.existsByCompany_IdAndDeviceUserIdAndIdNot(
                    TenantContext.getRequired(), deviceUserId, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "device punch id is already linked to another employee");
            }
        }
        applyRequest(entity, request, deviceUserId);

        if (request.status() != null) {
            entity.setStatus(request.status());
            entity.setTerminatedAt(request.status() == DomainEnums.HrEmployeeStatus.TERMINATED
                    ? LocalDateTime.now()
                    : null);
        }

        HrEmployee saved = repository.save(entity);
        int linked = deviceUserId != null ? linkUnmatchedPunches(saved) : 0;
        auditLogService.recordSuccess(
                AuditAction.HR_EMPLOYEE_UPDATED,
                actor,
                "HrEmployee",
                saved.getId(),
                Map.of("employeeNumber", saved.getEmployeeNumber(),
                        "linkedPunches", linked));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        Person actor = currentUserService.requireCurrentPerson();
        HrEmployee entity = requireEmployee(id);
        repository.delete(entity);
        // Punch rows survive with employee_id = null, so re-creating an employee
        // with the same punch ID recovers its history instead of losing it.
        auditLogService.recordSuccess(
                AuditAction.HR_EMPLOYEE_DELETED,
                actor,
                "HrEmployee",
                id,
                Map.of("employeeNumber", entity.getEmployeeNumber()));
    }

    HrEmployee requireEmployee(UUID id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "employee not found"));
    }

    /** Claims every punch whose device punch ID now matches this employee. */
    int linkUnmatchedPunches(HrEmployee employee) {
        String punchId = trimToNull(employee.getDeviceUserId());
        if (punchId == null) {
            return 0;
        }
        return attendanceRepository.linkUnmatchedToEmployee(
                TenantContext.getRequired(), employee, punchId);
    }

    private void applyRequest(HrEmployee entity, HrEmployeeRequest request, String deviceUserId) {
        if (request.fullName() != null) {
            entity.setFullName(requireText(request.fullName(), "fullName"));
        }
        if (request.departmentId() != null) {
            entity.setDepartment(departmentService.requireDepartment(request.departmentId()));
        }
        if (request.positionId() != null) {
            entity.setPosition(positionService.requirePosition(request.positionId()));
        }
        if (request.personId() != null) {
            entity.setPerson(personRepository.findScopedById(request.personId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "person not found")));
        }
        if (deviceUserId != null || request.deviceUserId() != null) {
            entity.setDeviceUserId(deviceUserId);
        }
        if (request.deviceUserPin() != null) {
            entity.setDeviceUserPin(trimToNull(request.deviceUserPin()));
        }
        if (request.cardNumber() != null) {
            entity.setCardNumber(trimToNull(request.cardNumber()));
        }
        if (request.nationalId() != null) {
            entity.setNationalId(trimToNull(request.nationalId()));
        }
        if (request.gender() != null) {
            entity.setGender(request.gender());
        }
        if (request.phone() != null) {
            entity.setPhone(trimToNull(request.phone()));
        }
        if (request.email() != null) {
            entity.setEmail(trimToNull(request.email()));
        }
        if (request.address() != null) {
            entity.setAddress(trimToNull(request.address()));
        }
        if (request.hireDate() != null) {
            entity.setHireDate(request.hireDate());
        }
        if (request.contractType() != null) {
            entity.setContractType(request.contractType());
        }
        if (request.contractEndDate() != null) {
            entity.setContractEndDate(request.contractEndDate());
        }
        if (request.basicSalary() != null) {
            BigDecimal salary = request.basicSalary();
            entity.setBasicSalary(salary.signum() < 0 ? BigDecimal.ZERO : salary);
        }
        if (request.notes() != null) {
            entity.setNotes(trimToNull(request.notes()));
        }
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    HrEmployeeResponse toResponse(HrEmployee e) {
        return new HrEmployeeResponse(
                e.getId(),
                e.getEmployeeNumber(),
                e.getFullName(),
                e.getDepartment() != null ? e.getDepartment().getId() : null,
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getPosition() != null ? e.getPosition().getId() : null,
                e.getPosition() != null ? e.getPosition().getTitle() : null,
                toPartySummary(e.getPerson()),
                e.getDeviceUserId(),
                e.getDeviceUserPin(),
                e.getCardNumber(),
                e.getNationalId(),
                e.getGender(),
                e.getPhone(),
                e.getEmail(),
                e.getAddress(),
                e.getHireDate(),
                e.getContractType(),
                e.getContractEndDate(),
                e.getBasicSalary(),
                e.getStatus(),
                e.getNotes(),
                e.getTerminatedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private static PartySummary toPartySummary(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(person.getId(), person.getFullName(), person.getSystemRole());
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireText(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return trimmed;
    }
}
