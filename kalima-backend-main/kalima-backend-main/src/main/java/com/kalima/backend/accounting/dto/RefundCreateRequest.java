package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Refund;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RefundCreateRequest(
        UUID invoiceId,
        UUID clientId,
        LocalDate refundDate,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        UUID bankAccountId,
        @Size(max = 50) String paymentMethod,
        @Size(max = 300) String reason,
        @Size(max = 1000) String notes) {
}