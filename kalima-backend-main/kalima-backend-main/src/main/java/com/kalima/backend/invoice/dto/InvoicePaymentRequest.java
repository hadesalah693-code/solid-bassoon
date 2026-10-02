package com.kalima.backend.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** تسجيل دفعة/تحصيل على فاتورة. */
public record InvoicePaymentRequest(

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount,

        @NotNull(message = "paymentDate is required")
        LocalDate paymentDate,

        @Size(max = 50, message = "method must be at most 50 characters")
        String method,

        /** الخزينة/الحساب البنكي الذي استُلم فيه المبلغ؛ يُستخدم في القيد المحاسلي. */
        UUID bankAccountId,

        @Size(max = 100, message = "reference must be at most 100 characters")
        String reference,

        @Size(max = 500, message = "notes must be at most 500 characters")
        String notes
) {
}
