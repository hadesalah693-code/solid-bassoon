package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Expense.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseCreateRequest(
        @NotNull(message = "expenseDate is required")
        LocalDate expenseDate,

        @NotNull(message = "category is required")
        Category category,

        @NotBlank(message = "description is required")
        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @Size(max = 200, message = "payee must be at most 200 characters")
        String payee,

        /** Ledger account to debit. Defaults to the category's account when null. */
        UUID accountId,

        UUID bankAccountId,

        UUID clientId,

        @NotNull(message = "subtotal is required")
        @DecimalMin(value = "0.0", message = "subtotal cannot be negative")
        BigDecimal subtotal,

        @DecimalMin(value = "0.0", message = "taxRate cannot be negative")
        BigDecimal taxRate,

        @Size(max = 50, message = "paymentMethod must be at most 50 characters")
        String paymentMethod,

        @Size(max = 100, message = "reference must be at most 100 characters")
        String reference,

        Boolean recurring,

        @Size(max = 1000, message = "notes must be at most 1000 characters")
        String notes) {
}
