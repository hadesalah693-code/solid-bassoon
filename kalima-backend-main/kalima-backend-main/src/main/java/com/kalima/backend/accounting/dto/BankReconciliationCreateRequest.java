package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BankReconciliationCreateRequest(
        @NotNull UUID bankAccountId,
        @NotNull LocalDate statementDate,
        @NotNull BigDecimal statementClosingBalance,
        @Size(max = 1000) String notes) {
}