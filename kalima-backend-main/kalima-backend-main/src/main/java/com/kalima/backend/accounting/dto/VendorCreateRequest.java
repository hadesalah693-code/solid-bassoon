package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Vendor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record VendorCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String nameAr,
        @Email @Size(max = 200) String email,
        @Size(max = 50) String phone,
        @Size(max = 50) String taxNumber,
        @Size(max = 300) String address,
        UUID expenseAccountId,
        @Size(max = 1000) String notes) {
}