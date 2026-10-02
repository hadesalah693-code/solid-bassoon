package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Refund;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        String refundNumber,
        LocalDate refundDate,
        UUID invoiceId,
        String invoiceNumber,
        UUID clientId,
        String clientName,
        BigDecimal amount,
        String paymentMethod,
        UUID bankAccountId,
        String bankAccountName,
        String reason,
        String notes,
        UUID journalEntryId,
        boolean voided,
        Instant voidedAt) {

    public static RefundResponse from(Refund r) {
        return new RefundResponse(
                r.getId(),
                r.getRefundNumber(),
                r.getRefundDate(),
                r.getInvoice() == null ? null : r.getInvoice().getId(),
                r.getInvoice() == null ? null : r.getInvoice().getInvoiceNumber(),
                r.getClient() == null ? null : r.getClient().getId(),
                r.getClient() == null ? null : r.getClient().getFullName(),
                r.getAmount(),
                r.getPaymentMethod(),
                r.getBankAccount() == null ? null : r.getBankAccount().getId(),
                r.getBankAccount() == null ? null : r.getBankAccount().getNameAr(),
                r.getReason(),
                r.getNotes(),
                r.getJournalEntry() == null ? null : r.getJournalEntry().getId(),
                r.getVoidedAt() != null,
                r.getVoidedAt());
    }
}