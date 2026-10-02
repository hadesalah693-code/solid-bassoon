package com.kalima.backend.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffUpdateRequest(
        @NotBlank(message = "fullName is required")
        @Size(max = 150, message = "fullName must be at most 150 characters")
        String fullName,

        @Size(max = 50, message = "nationalId must be at most 50 characters")
        String nationalId,

        @Size(max = 30, message = "phone must be at most 30 characters")
        String phone,

        String address,

        String notes
) {
}
