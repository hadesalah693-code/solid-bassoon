package com.kalima.backend.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Result of posting an opening-balance entry, including the resulting ledger balances. */
public record OpeningBalanceResponse(
        UUID journalEntryId,
        String entryNumber,
        LocalDate effectiveDate,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        List<AccountBalance> accounts,
        String createdBy
) {
    public record AccountBalance(
            UUID accountId,
            String code,
            String nameAr,
            BigDecimal balance
    ) {
    }
}
