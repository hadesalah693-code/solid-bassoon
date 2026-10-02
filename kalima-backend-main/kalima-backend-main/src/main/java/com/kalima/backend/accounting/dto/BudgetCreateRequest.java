package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Budget;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetCreateRequest(
        @NotBlank @Size(max = 200) String name,
        Integer fiscalYear,
        Budget.Period period,
        Integer periodMonth,
        Integer quarter,
        LocalDate periodStart,
        LocalDate periodEnd,
        UUID accountId,
        @NotNull @DecimalMin("0") BigDecimal budgetAmount,
        @Size(max = 1000) String notes) {
}