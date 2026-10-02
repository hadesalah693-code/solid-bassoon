package com.kalima.backend.officesettings.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.officesettings.dto.OfficeSettingsResponse;
import com.kalima.backend.officesettings.dto.OfficeSettingsUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class OfficeSettingsService {

    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public OfficeSettingsService(CompanyRepository companyRepository,
                                 CurrentUserService currentUserService,
                                 AuditLogService auditLogService) {
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public OfficeSettingsResponse getCurrent() {
        Company company = requireCurrentCompany();
        return toResponse(company);
    }

    public OfficeSettingsResponse update(OfficeSettingsUpdateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        Company company = requireCurrentCompany();

        Map<String, Object> changes = new LinkedHashMap<>();
        trackChange(changes, "name", company.getName(), request.name());
        trackChange(changes, "legalName", company.getLegalName(), trimToNull(request.legalName()));
        trackChange(changes, "commercialRegister", company.getCommercialRegister(),
                trimToNull(request.commercialRegister()));
        trackChange(changes, "taxNumber", company.getTaxNumber(), trimToNull(request.taxNumber()));
        trackChange(changes, "phone", company.getPhone(), trimToNull(request.phone()));
        trackChange(changes, "email", company.getEmail(), trimToNull(request.email()));
        trackChange(changes, "address", company.getAddress(), trimToNull(request.address()));
        trackChange(changes, "website", company.getWebsite(), trimToNull(request.website()));
        trackChange(changes, "city", company.getCity(), trimToNull(request.city()));

        String countryCode = request.countryCode() != null
                ? request.countryCode().trim().toUpperCase()
                : company.getCountryCode();
        trackChange(changes, "countryCode", company.getCountryCode(), countryCode);

        company.setName(request.name().trim());
        company.setLegalName(trimToNull(request.legalName()));
        company.setCommercialRegister(trimToNull(request.commercialRegister()));
        company.setTaxNumber(trimToNull(request.taxNumber()));
        company.setPhone(trimToNull(request.phone()));
        company.setEmail(trimToNull(request.email()));
        company.setAddress(trimToNull(request.address()));
        company.setWebsite(trimToNull(request.website()));
        company.setCity(trimToNull(request.city()));
        company.setCountryCode(countryCode);

        Company saved = companyRepository.save(company);

        if (!changes.isEmpty()) {
            auditLogService.recordSuccess(
                    AuditAction.OFFICE_SETTINGS_UPDATED,
                    actor,
                    "Company",
                    saved.getId(),
                    Map.of("changes", changes));
        }

        return toResponse(saved);
    }

    private Company requireCurrentCompany() {
        UUID companyId = TenantContext.getRequired();
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "office not found"));
    }

    private static OfficeSettingsResponse toResponse(Company company) {
        return new OfficeSettingsResponse(
                company.getId(),
                company.getName(),
                company.getLegalName(),
                company.getCommercialRegister(),
                company.getTaxNumber(),
                company.getPhone(),
                company.getEmail(),
                company.getAddress(),
                company.getWebsite(),
                company.getCity(),
                company.getCountryCode(),
                company.isActive(),
                company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private static void trackChange(Map<String, Object> changes,
                                    String field,
                                    Object before,
                                    Object after) {
        if (!valuesEqual(before, after)) {
            changes.put(field, Map.of("before", before, "after", after));
        }
    }

    private static boolean valuesEqual(Object before, Object after) {
        if (before == null && after == null) {
            return true;
        }
        if (before == null || after == null) {
            return false;
        }
        return before.equals(after);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
