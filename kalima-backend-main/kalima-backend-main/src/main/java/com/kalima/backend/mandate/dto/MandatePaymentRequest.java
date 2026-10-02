package com.kalima.backend.mandate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request body for recording a payment on a mandate. */
public record MandatePaymentRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount,

        @NotNull(message = "paymentDate is required")
        LocalDate paymentDate,

        @Size(max = 50, message = "method must be at most 50 characters")
        String method,

        String notes
) {
}
