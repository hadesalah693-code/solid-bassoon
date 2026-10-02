package com.kalima.backend.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        String receiptNumber,
        LocalDate receiptDate,
        UUID clientId,
        String clientName,
        UUID invoiceId,
        UUID bankAccountId,
        String bankAccountName,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String reference,
        boolean deposit,
        UUID journalEntryId,
        String journalEntryNumber,
        String notes,
        String recordedBy,
        LocalDate createdAt) {
}
