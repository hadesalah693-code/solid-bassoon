package com.kalima.backend.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AllocationResponse(
        UUID id,
        UUID receiptId,
        String receiptNumber,
        UUID invoiceId,
        String invoiceNumber,
        UUID clientId,
        String clientName,
        BigDecimal amount,
        LocalDate allocationDate,
        BigDecimal invoiceTotal,
        BigDecimal invoiceAmountPaid,
        BigDecimal invoiceRemaining,
        boolean voided,
        LocalDate voidedOn,
        UUID reversalJournalEntryId,
        String notes) {
}