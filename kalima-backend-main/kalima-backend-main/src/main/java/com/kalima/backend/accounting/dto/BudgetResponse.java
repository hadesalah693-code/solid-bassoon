package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        String name,
        int fiscalYear,
        Budget.Period period,
        LocalDate periodStart,
        LocalDate periodEnd,
        UUID accountId,
        String accountCode,
        String accountName,
        BigDecimal budgetAmount,
        /** Actual spend, read from the ledger rather than stored on the budget. */
        BigDecimal actualAmount,
        BigDecimal variance,
        BigDecimal utilisationPercent,
        boolean overBudget,
        String notes) {

    public static BudgetResponse from(Budget b, BigDecimal actual) {
        BigDecimal a = actual == null ? BigDecimal.ZERO : actual;
        BigDecimal budget = b.getBudgetAmount() == null ? BigDecimal.ZERO : b.getBudgetAmount();
        BigDecimal variance = budget.subtract(a);
        BigDecimal utilisation = budget.signum() == 0
                ? BigDecimal.ZERO
                : a.multiply(new BigDecimal("100")).divide(budget, 2,
                        java.math.RoundingMode.HALF_UP);
        return new BudgetResponse(
                b.getId(),
                b.getName(),
                b.getFiscalYear(),
                b.getPeriod(),
                b.getPeriodStart(),
                b.getPeriodEnd(),
                b.getAccount() == null ? null : b.getAccount().getId(),
                b.getAccount() == null ? null : b.getAccount().getCode(),
                b.getAccount() == null ? null : b.getAccount().getNameAr(),
                budget,
                a,
                variance,
                utilisation,
                a.compareTo(budget) > 0,
                b.getNotes());
    }
}