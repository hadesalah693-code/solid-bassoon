package com.kalima.backend.staff.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffCreateRequest(
        @NotNull(message = "systemRole is required")
        DomainEnums.SystemRole systemRole,

        @NotBlank(message = "fullName is required")
        @Size(max = 150, message = "fullName must be at most 150 characters")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 150, message = "email must be at most 150 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
        String password,

        @Size(max = 50, message = "nationalId must be at most 50 characters")
        String nationalId,

        @Size(max = 30, message = "phone must be at most 30 characters")
        String phone,

        String address,

        String notes
) {
}
