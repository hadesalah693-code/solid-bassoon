package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.BankStatementLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StatementLineResponse(
        UUID id,
        UUID reconciliationId,
        UUID bankAccountId,
        LocalDate lineDate,
        String description,
        String reference,
        BigDecimal amount,
        BankStatementLine.MatchType matchedType,
        UUID matchedId,
        Instant matchedAt,
        boolean matched,
        String notes) {

    public static StatementLineResponse from(BankStatementLine l) {
        return new StatementLineResponse(
                l.getId(),
                l.getReconciliation() == null ? null : l.getReconciliation().getId(),
                l.getBankAccount() == null ? null : l.getBankAccount().getId(),
                l.getLineDate(),
                l.getDescription(),
                l.getReference(),
                l.getAmount(),
                l.getMatchedType(),
                l.getMatchedId(),
                l.getMatchedAt(),
                l.getMatchedId() != null,
                l.getNotes());
    }
}