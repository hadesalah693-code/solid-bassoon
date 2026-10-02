package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BankTransferRequest(
        @NotNull UUID sourceBankAccountId,
        @NotNull UUID destinationBankAccountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull LocalDate transferDate,
        @Size(max = 100) String reference,
        @Size(max = 1000) String notes
) {
}