package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TaxRateRequest(
        @NotBlank(message = "nameAr is required")
        @Size(max = 100, message = "nameAr must be at most 100 characters")
        String nameAr,

        @NotNull(message = "rate is required")
        @DecimalMin(value = "0.0", message = "rate cannot be negative")
        @DecimalMax(value = "100.0", message = "rate cannot exceed 100")
        BigDecimal rate,

        UUID accountId,

        Boolean active,

        Boolean defaultRate) {
}
