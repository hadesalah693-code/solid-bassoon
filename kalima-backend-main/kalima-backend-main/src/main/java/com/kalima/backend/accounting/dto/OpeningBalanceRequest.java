package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Opening balances for a new office. Every line names an existing ledger account and
 * a signed amount: positive debits the account, negative credits it. The service
 * rejects revenue and expense accounts, because a P&L cannot be opened.
 */
public record OpeningBalanceRequest(

        @NotNull
        String effectiveDate,

        String notes,

        @NotNull
        @DecimalMin(value = "0.01", message = "يجب إدخال سطر واحد على الأقل")
        List<Line> lines
) {
    public record Line(
            @NotNull UUID accountId,
            @NotNull BigDecimal amount
    ) {
    }
}
