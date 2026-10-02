package com.kalima.backend.officesettings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OfficeSettingsUpdateRequest(
        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @Size(max = 250, message = "legalName must be at most 250 characters")
        String legalName,

        @Size(max = 100, message = "commercialRegister must be at most 100 characters")
        String commercialRegister,

        @Size(max = 100, message = "taxNumber must be at most 100 characters")
        String taxNumber,

        @Size(max = 30, message = "phone must be at most 30 characters")
        String phone,

        @Email(message = "email must be valid")
        @Size(max = 150, message = "email must be at most 150 characters")
        String email,

        @Size(max = 2000, message = "address must be at most 2000 characters")
        String address,

        @Size(max = 200, message = "website must be at most 200 characters")
        String website,

        @Size(max = 100, message = "city must be at most 100 characters")
        String city,

        @Pattern(regexp = "^[A-Z]{2}$", message = "countryCode must be a 2-letter ISO code")
        String countryCode
) {
}
