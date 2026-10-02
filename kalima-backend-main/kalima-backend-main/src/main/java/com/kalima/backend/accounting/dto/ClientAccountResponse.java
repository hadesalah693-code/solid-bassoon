package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Expense.Category;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientAccountResponse(
        UUID id,
        UUID personId,
        String clientName,
        UUID accountId,
        String accountCode,
        BigDecimal openingBalance,
        BigDecimal balance,
        BigDecimal creditLimit,
        BigDecimal invoiced,
        BigDecimal paid,
        BigDecimal outstanding,
        String notes) {
}
