package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Account.Type;
import com.kalima.backend.accounting.entity.JournalEntry.Source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AccountingReportResponse(
        LocalDate from,
        LocalDate to,

        /* --- Profit & loss --- */
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal grossProfit,
        BigDecimal operatingExpenses,
        BigDecimal netProfit,
        List<AccountAmount> revenueBreakdown,
        List<AccountAmount> expenseBreakdown,

        /* --- Balance sheet --- */
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        BigDecimal totalLiabilitiesAndEquity,
        boolean balanced,
        List<AccountAmount> assets,
        List<AccountAmount> liabilities,
        List<AccountAmount> equity,

        /* --- Trial balance --- */
        List<TrialRow> trialBalance,
        BigDecimal trialTotalDebit,
        BigDecimal trialTotalCredit,
        boolean trialBalanced,

        /* --- Cash --- */
        BigDecimal cashOnHand,
        BigDecimal cashIn,
        BigDecimal cashOut,
        List<BankBalance> bankBalances,

        /* --- Receivables --- */
        BigDecimal totalReceivable,
        long overdueInvoiceCount,
        List<AgingBucket> aging,

        /* --- Tax --- */
        BigDecimal outputVat,
        BigDecimal inputVat,
        BigDecimal netVatPayable) {

    public record AccountAmount(
            UUID accountId,
            String code,
            String nameAr,
            Type type,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance) {
    }

    public record TrialRow(
            UUID accountId,
            String code,
            String nameAr,
            Type type,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance) {
    }

    public record BankBalance(
            UUID id,
            String nameAr,
            String bankName,
            String currency,
            BigDecimal openingBalance,
            BigDecimal movement,
            BigDecimal balance) {
    }

    public record AgingBucket(String label, BigDecimal amount, long count) {
    }

    public record StatementRow(
            LocalDate date,
            String reference,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance) {
    }

    public record ClientStatementResponse(
            UUID clientId,
            String clientName,
            LocalDate from,
            LocalDate to,
            BigDecimal openingBalance,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal closingBalance,
            List<StatementRow> rows) {
    }
}
