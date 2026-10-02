package com.kalima.backend.person.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonRequest(
        @NotNull(message = "personType is required")
        DomainEnums.PersonType personType,

        DomainEnums.SystemRole systemRole,

        @Size(max = 150, message = "fullName must be at most 150 characters")
        String fullName,

        @Size(max = 50, message = "nationalId must be at most 50 characters")
        String nationalId,

        @Size(max = 200, message = "companyName must be at most 200 characters")
        String companyName,

        @Size(max = 100, message = "commercialRegister must be at most 100 characters")
        String commercialRegister,

        @Size(max = 30, message = "phone must be at most 30 characters")
        String phone,

        @Email(message = "email must be a valid email address")
        @Size(max = 150, message = "email must be at most 150 characters")
        String email,

        String address,

        String notes,

        @Size(min = 6, max = 100, message = "password must be between 6 and 100 characters")
        String password
) {
}
