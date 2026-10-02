package com.kalima.backend.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** سجل دفعة واحدة على فاتورة، مع القيد المحاسلي الذي ترحّل لها. */
public record InvoicePaymentResponse(
        UUID id,
        UUID invoiceId,
        BigDecimal amount,
        LocalDate paymentDate,
        String method,
        UUID bankAccountId,
        String bankAccountName,
        String reference,
        String notes,
        UUID journalEntryId,
        String journalEntryNumber,
        String recordedBy,
        LocalDate createdAt
) {
}
