package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementLineCreateRequest(
        LocalDate lineDate,
        @Size(max = 300) String description,
        @Size(max = 100) String reference,
        @NotNull BigDecimal amount,
        @Size(max = 1000) String notes) {
}