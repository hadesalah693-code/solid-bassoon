package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Account;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccountLedgerResponse(
        UUID accountId,
        String accountCode,
        String accountName,
        Account.Type accountType,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal periodDebit,
        BigDecimal periodCredit,
        BigDecimal closingBalance,
        Page<Row> entries
) {
    public record Row(
            LocalDate date,
            String entryNumber,
            String referenceType,
            UUID referenceId,
            String description,
            BigDecimal debit,
            BigDecimal credit
    ) {
    }
}