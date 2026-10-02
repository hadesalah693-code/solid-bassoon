package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.RecurringInvoice;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringInvoiceCreateRequest(
        @NotNull UUID clientId,
        UUID caseId,
        @NotNull @Size(max = 500) String description,
        @NotNull RecurringInvoice.Frequency frequency,
        Integer intervalCount,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull @DecimalMin(value = "0.01") BigDecimal subtotal,
        @DecimalMin("0") BigDecimal taxRate,
        @DecimalMin("0") BigDecimal taxAmount,
        @Size(max = 1000) String notes) {
}