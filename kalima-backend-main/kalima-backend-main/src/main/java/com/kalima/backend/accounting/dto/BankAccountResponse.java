package com.kalima.backend.accounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BankAccountResponse(
        UUID id,
        String nameAr,
        String bankName,
        String iban,
        String accountNumber,
        String currency,
        UUID accountId,
        String accountCode,
        BigDecimal openingBalance,
        BigDecimal movement,
        BigDecimal balance,
        boolean active) {
}
