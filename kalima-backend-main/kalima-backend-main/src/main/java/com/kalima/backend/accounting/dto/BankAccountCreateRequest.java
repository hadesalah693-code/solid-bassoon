package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record BankAccountCreateRequest(
        @NotBlank(message = "nameAr is required")
        @Size(max = 150, message = "nameAr must be at most 150 characters")
        String nameAr,

        /** Ledger account to use. Defaults to 1100 (cash) when null. */
        UUID accountId,

        @Size(max = 150, message = "bankName must be at most 150 characters")
        String bankName,

        @Size(max = 50, message = "iban must be at most 50 characters")
        String iban,

        @Size(max = 50, message = "accountNumber must be at most 50 characters")
        String accountNumber,

        @Size(max = 3, min = 3, message = "currency must be a 3-letter code")
        String currency,

        @jakarta.validation.constraints.DecimalMin(value = "0.0", message = "openingBalance cannot be negative")
        BigDecimal openingBalance) {
}
