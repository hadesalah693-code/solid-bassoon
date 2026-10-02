package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Expense.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        String expenseNumber,
        LocalDate expenseDate,
        Category category,
        String payee,
        String description,
        UUID accountId,
        String accountCode,
        String accountName,
        UUID bankAccountId,
        String bankAccountName,
        UUID clientId,
        String clientName,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        String paymentMethod,
        String reference,
        boolean recurring,
        UUID journalEntryId,
        String journalEntryNumber,
        String notes,
        String recordedBy,
        LocalDate createdAt) {
}
