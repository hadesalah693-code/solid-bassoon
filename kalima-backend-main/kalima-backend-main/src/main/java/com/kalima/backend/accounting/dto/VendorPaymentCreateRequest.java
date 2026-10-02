package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.VendorPayment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VendorPaymentCreateRequest(
        @NotNull UUID billId,
        LocalDate paymentDate,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull UUID bankAccountId,
        @Size(max = 50) String method,
        @Size(max = 100) String reference,
        @Size(max = 1000) String notes) {
}