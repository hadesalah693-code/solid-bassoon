package com.kalima.backend.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BankTransferResponse(
        UUID id,
        UUID sourceBankAccountId,
        String sourceBankAccountName,
        UUID destinationBankAccountId,
        String destinationBankAccountName,
        BigDecimal amount,
        String currency,
        LocalDate transferDate,
        String reference,
        String notes,
        UUID journalEntryId,
        String journalEntryNumber
) {
}