package com.kalima.backend.officesettings.controller;

import com.kalima.backend.officesettings.dto.OfficeSettingsResponse;
import com.kalima.backend.officesettings.dto.OfficeSettingsUpdateRequest;
import com.kalima.backend.officesettings.service.OfficeSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/office/settings")
public class OfficeSettingsController {

    private final OfficeSettingsService officeSettingsService;

    public OfficeSettingsController(OfficeSettingsService officeSettingsService) {
        this.officeSettingsService = officeSettingsService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public OfficeSettingsResponse get() {
        return officeSettingsService.getCurrent();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public OfficeSettingsResponse update(@Valid @RequestBody OfficeSettingsUpdateRequest request) {
        return officeSettingsService.update(request);
    }
}
