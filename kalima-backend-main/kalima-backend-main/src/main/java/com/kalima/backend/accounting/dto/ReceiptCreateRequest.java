package com.kalima.backend.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReceiptCreateRequest(
        @NotNull(message = "receiptDate is required")
        LocalDate receiptDate,

        @NotNull(message = "bankAccountId is required")
        UUID bankAccountId,

        @NotNull(message = "amount is required")
        @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount,

        UUID clientId,

        /**
         * Single-invoice shortcut kept for existing callers; internally it becomes
         * exactly one allocation row so both paths share one code path.
         */
        UUID invoiceId,

        /**
         * Splits the receipt over several invoices, or applies part of it and leaves
         * the rest on the client credit account. Mutually exclusive with
         * {@code invoiceId} in practice; when both are sent the allocations win.
         */
        List<@Valid AllocationLineRequest> allocations,

        @Size(max = 3, min = 3, message = "currency must be a 3-letter code")
        String currency,

        @Size(max = 50, message = "paymentMethod must be at most 50 characters")
        String paymentMethod,

        @Size(max = 100, message = "reference must be at most 100 characters")
        String reference,

        Boolean deposit,

        @Size(max = 1000, message = "notes must be at most 1000 characters")
        String notes) {
}
