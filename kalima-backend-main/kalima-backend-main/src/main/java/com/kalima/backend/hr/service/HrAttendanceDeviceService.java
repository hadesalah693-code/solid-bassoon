package com.kalima.backend.hr.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hr.device.ZkDeviceException;
import com.kalima.backend.hr.device.ZkTecoClient;
import com.kalima.backend.hr.dto.HrAttendanceDeviceRequest;
import com.kalima.backend.hr.dto.HrAttendanceDeviceResponse;
import com.kalima.backend.hr.dto.HrDeviceTestResponse;
import com.kalima.backend.hr.entity.HrAttendanceDevice;
import com.kalima.backend.hr.repository.HrAttendanceDeviceSpecifications;
import com.kalima.backend.hr.repository.HrAttendanceDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class HrAttendanceDeviceService {

    private static final Logger log = LoggerFactory.getLogger(HrAttendanceDeviceService.class);
    private static final int DEFAULT_PORT = 4370;

    private final HrAttendanceDeviceRepository repository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final ZkTecoClient zkTecoClient;

    public HrAttendanceDeviceService(HrAttendanceDeviceRepository repository,
                                     CompanyRepository companyRepository,
                                     CurrentUserService currentUserService,
                                     AuditLogService auditLogService,
                                     ZkTecoClient zkTecoClient) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.zkTecoClient = zkTecoClient;
    }

    @Transactional(readOnly = true)
    public Page<HrAttendanceDeviceResponse> search(DomainEnums.HrDeviceProtocol protocol,
                                                   DomainEnums.HrDeviceStatus status,
                                                   String search,
                                                   Pageable pageable) {
        return repository.findAll(
                        HrAttendanceDeviceSpecifications.filtered(protocol, status, trimToNull(search)),
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public HrAttendanceDeviceResponse findById(UUID id) {
        return toResponse(requireDevice(id));
    }

    public HrAttendanceDeviceResponse create(HrAttendanceDeviceRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        String ipAddress = requireText(request.ipAddress(), "ipAddress");
        UUID tenantId = TenantContext.getRequired();
        if (repository.existsByCompany_IdAndIpAddress(tenantId, ipAddress)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a device with this IP is already registered");
        }

        HrAttendanceDevice entity = new HrAttendanceDevice();
        entity.setCompany(currentTenantReference());
        entity.setName(requireText(request.name(), "name"));
        entity.setLocation(trimToNull(request.location()));
        entity.setIpAddress(ipAddress);
        entity.setPort(resolvePort(request.port()));
        entity.setProtocol(request.protocol() == null ? DomainEnums.HrDeviceProtocol.ZK_TECO_TCP : request.protocol());
        entity.setSerialNumber(trimToNull(request.serialNumber()));
        entity.setStatus(request.status() == null ? DomainEnums.HrDeviceStatus.ACTIVE : request.status());

        HrAttendanceDevice saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.HR_DEVICE_CREATED,
                actor,
                "HrAttendanceDevice",
                saved.getId(),
                Map.of("name", saved.getName(), "ipAddress", saved.getIpAddress()));
        return toResponse(saved);
    }

    public HrAttendanceDeviceResponse update(UUID id, HrAttendanceDeviceRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        HrAttendanceDevice entity = requireDevice(id);

        if (request.name() != null) {
            entity.setName(requireText(request.name(), "name"));
        }
        if (request.location() != null) {
            entity.setLocation(trimToNull(request.location()));
        }
        if (request.ipAddress() != null) {
            String ipAddress = requireText(request.ipAddress(), "ipAddress");
            if (repository.existsByCompany_IdAndIpAddressAndIdNot(TenantContext.getRequired(), ipAddress, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "a device with this IP is already registered");
            }
            entity.setIpAddress(ipAddress);
        }
        if (request.port() != null) {
            entity.setPort(resolvePort(request.port()));
        }
        if (request.protocol() != null) {
            entity.setProtocol(request.protocol());
        }
        if (request.serialNumber() != null) {
            entity.setSerialNumber(trimToNull(request.serialNumber()));
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        HrAttendanceDevice saved = repository.save(entity);
        auditLogService.recordSuccess(
                AuditAction.HR_DEVICE_UPDATED,
                actor,
                "HrAttendanceDevice",
                saved.getId(),
                Map.of("name", saved.getName(), "ipAddress", saved.getIpAddress()));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        Person actor = currentUserService.requireCurrentPerson();
        HrAttendanceDevice entity = requireDevice(id);
        repository.delete(entity);
        auditLogService.recordSuccess(
                AuditAction.HR_DEVICE_DELETED,
                actor,
                "HrAttendanceDevice",
                id,
                Map.of("name", entity.getName()));
    }

    /**
     * Connectivity check for the "test" button. A failure is reported as
     * {@code reachable=false} rather than an error so the UI can show the device's
     * own message next to the form instead of a generic failure banner.
     */
    @Transactional(readOnly = true)
    public HrDeviceTestResponse testConnection(UUID id) {
        HrAttendanceDevice device = requireDevice(id);
        return testConnection(device.getIpAddress(), device.getPort());
    }

    /** Connectivity check for an unsaved device, so staff can test before saving. */
    @Transactional(readOnly = true)
    public HrDeviceTestResponse testUnsaved(String ipAddress, Integer port) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ipAddress is required");
        }
        return testConnection(ipAddress.trim(), resolvePort(port));
    }

    private HrDeviceTestResponse testConnection(String ipAddress, int port) {
        try {
            ZkTecoClient.ZkDeviceInfo info = zkTecoClient.probe(ipAddress, port);
            return new HrDeviceTestResponse(
                    true,
                    ipAddress,
                    port,
                    info.serialNumber(),
                    info.firmware(),
                    info.deviceTime(),
                    "تم الاتصال بالجهاز بنجاح");
        } catch (ZkDeviceException e) {
            log.warn("Device connection test failed for {}:{}", ipAddress, port, e);
            return new HrDeviceTestResponse(false, ipAddress, port, null, null, null, e.getMessage());
        }
    }

    HrAttendanceDevice requireDevice(UUID id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "device not found"));
    }

    /** Records the outcome of a sync so the devices list can show it. */
    void markSynced(HrAttendanceDevice device, String message) {
        device.setLastSyncAt(LocalDateTime.now());
        device.setLastSyncMessage(truncate(message, 500));
    }

    /** Flags a device whose last sync failed, without touching its punch history. */
    void markSyncFailed(HrAttendanceDevice device, String message) {
        device.setLastSyncAt(LocalDateTime.now());
        device.setLastSyncMessage(truncate(message, 500));
    }

    HrAttendanceDeviceResponse toResponse(HrAttendanceDevice d) {
        return new HrAttendanceDeviceResponse(
                d.getId(),
                d.getName(),
                d.getLocation(),
                d.getIpAddress(),
                d.getPort(),
                d.getProtocol(),
                d.getSerialNumber(),
                d.getStatus(),
                d.getLastSyncAt(),
                d.getLastSyncMessage());
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private static int resolvePort(Integer port) {
        if (port == null) {
            return DEFAULT_PORT;
        }
        if (port < 1 || port > 65535) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "port must be between 1 and 65535");
        }
        return port;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String trimToNull(String value) {
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
