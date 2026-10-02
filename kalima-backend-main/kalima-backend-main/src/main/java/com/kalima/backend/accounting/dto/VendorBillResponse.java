package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.VendorBill;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VendorBillResponse(
        UUID id,
        String billNumber,
        UUID vendorId,
        String vendorName,
        LocalDate billDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal remaining,
        VendorBill.Status status,
        boolean overdue,
        UUID expenseAccountId,
        String reference,
        String notes,
        UUID journalEntryId,
        boolean voided,
        Instant voidedAt) {

    public static VendorBillResponse from(VendorBill b, LocalDate today) {
        boolean overdue = today != null
                && b.getDueDate() != null
                && b.getDueDate().isBefore(today)
                && b.getRemaining().signum() > 0;
        return new VendorBillResponse(
                b.getId(),
                b.getBillNumber(),
                b.getVendor() == null ? null : b.getVendor().getId(),
                b.getVendor() == null ? null : b.getVendor().getName(),
                b.getBillDate(),
                b.getDueDate(),
                b.getSubtotal(),
                b.getTaxRate(),
                b.getTaxAmount(),
                b.getTotal(),
                b.getAmountPaid(),
                b.getRemaining(),
                b.getStatus(),
                overdue,
                b.getExpenseAccount() == null ? null : b.getExpenseAccount().getId(),
                b.getReference(),
                b.getNotes(),
                b.getJournalEntry() == null ? null : b.getJournalEntry().getId(),
                b.getVoidedAt() != null,
                b.getVoidedAt());
    }
}