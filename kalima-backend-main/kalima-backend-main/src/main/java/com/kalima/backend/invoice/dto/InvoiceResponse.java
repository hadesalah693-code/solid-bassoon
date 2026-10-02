package com.kalima.backend.invoice.dto;

import com.kalima.backend.invoice.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** تفاصيل فاتورة كاملة (عرض في التفاصيل والتعديل). */
public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        InvoiceStatus status,
        UUID companyId,
        UUID clientId,
        String clientName,
        UUID caseId,
        String caseNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal discount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal remaining,
        String notes,
        String recordedBy,
        String recordedByEmail,
        LocalDateTime createdAt,
        UUID journalEntryId,
        String journalEntryNumber,
        List<InvoicePaymentResponse> payments
) {
}
