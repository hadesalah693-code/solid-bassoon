package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Turns the unbilled time on one case into an invoice.
 *
 * <p>No amount is supplied: the invoice is worth exactly the unbilled work, so a
 * mistyped figure cannot put a number in the ledger that the timesheets do not
 * support. Only the dates and the tax treatment are the caller's to make.
 */
public record TimeBillingRequest(
        @NotNull(message = "clientId is required") UUID clientId,
        LocalDate issueDate,
        LocalDate dueDate,
        @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
        @Size(max = 1000) String notes) {
}