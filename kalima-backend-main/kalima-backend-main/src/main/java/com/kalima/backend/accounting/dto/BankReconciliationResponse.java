package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.BankReconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BankReconciliationResponse(
        UUID id,
        UUID bankAccountId,
        String bankAccountName,
        LocalDate statementDate,
        BigDecimal statementClosingBalance,
        BigDecimal reconciledBalance,
        BigDecimal difference,
        BankReconciliation.Status status,
        Instant completedAt,
        String notes,
        Instant createdAt) {

    public static BankReconciliationResponse from(BankReconciliation r) {
        return new BankReconciliationResponse(
                r.getId(),
                r.getBankAccount() == null ? null : r.getBankAccount().getId(),
                r.getBankAccount() == null ? null : r.getBankAccount().getNameAr(),
                r.getStatementDate(),
                r.getStatementClosingBalance(),
                r.getReconciledBalance(),
                r.getDifference(),
                r.getStatus(),
                r.getCompletedAt(),
                r.getNotes(),
                r.getCreatedAt());
    }
}