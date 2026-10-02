package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ClientAccountRequest(
        @DecimalMin(value = "0.0", message = "openingBalance cannot be negative")
        BigDecimal openingBalance,

        @DecimalMin(value = "0.0", message = "creditLimit cannot be negative")
        BigDecimal creditLimit,

        @Size(max = 1000, message = "notes must be at most 1000 characters")
        String notes
) {
}
