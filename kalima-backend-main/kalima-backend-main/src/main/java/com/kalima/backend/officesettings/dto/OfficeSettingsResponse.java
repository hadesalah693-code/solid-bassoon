package com.kalima.backend.officesettings.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OfficeSettingsResponse(
        UUID id,
        String name,
        String legalName,
        String commercialRegister,
        String taxNumber,
        String phone,
        String email,
        String address,
        String website,
        String city,
        String countryCode,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
