package com.kalima.backend.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Applies an already-issued receipt to one or more invoices.
 *
 * <p>Only unapplied receipts can be allocated: a receipt whose money already
 * relieved AR on issue has nothing left to move.
 */
public record AllocationCreateRequest(
        @NotNull(message = "receiptId is required")
        UUID receiptId,

        @NotNull(message = "allocations must not be empty")
        @Valid
        List<AllocationLineRequest> allocations,

        LocalDate allocationDate,

        @jakarta.validation.constraints.Size(max = 1000, message = "notes must be at most 1000 characters")
        String notes) {

    public BigDecimal total() {
        if (allocations == null) {
            return BigDecimal.ZERO;
        }
        return allocations.stream()
                .filter(a -> a.amount() != null)
                .map(AllocationLineRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}