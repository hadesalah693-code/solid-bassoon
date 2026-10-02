package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RetainerCreateRequest(
        @NotNull UUID clientId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currency,
        @NotNull LocalDate receivedDate,
        String reference,
        String notes
) {
}