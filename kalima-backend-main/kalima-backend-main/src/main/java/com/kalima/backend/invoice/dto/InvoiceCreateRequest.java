package com.kalima.backend.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** إنشاء فاتورة تحصيل على قضية/توكيل — المتاح: المحاسب والسكرتارية والمحامي. */
public record InvoiceCreateRequest(

        @NotNull(message = "clientId is required")
        UUID clientId,

        UUID caseId,

        @NotNull(message = "issueDate is required")
        LocalDate issueDate,

        LocalDate dueDate,

        @NotNull(message = "subtotal is required")
        @DecimalMin(value = "0.0", message = "subtotal cannot be negative")
        BigDecimal subtotal,

        @DecimalMin(value = "0.0", message = "taxRate cannot be negative")
        BigDecimal taxRate,

        @DecimalMin(value = "0.0", message = "discount cannot be negative")
        BigDecimal discount,

        @Size(max = 2000, message = "notes must be at most 2000 characters")
        String notes
) {
}
