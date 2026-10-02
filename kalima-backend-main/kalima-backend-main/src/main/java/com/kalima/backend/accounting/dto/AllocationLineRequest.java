package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One invoice's share of a receipt.
 *
 * <p>Replaces the single {@code receipts.invoice_id} column for callers that
 * spread one receipt over several invoices, or apply it in stages.
 */
public record AllocationLineRequest(
        @NotNull(message = "invoiceId is required")
        UUID invoiceId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount) {
}