package com.kalima.backend.invoice.dto;

import com.kalima.backend.invoice.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** صف في قائمة الفواتير. */
public record InvoiceListItemResponse(
        UUID id,
        String invoiceNumber,
        InvoiceStatus status,
        String clientName,
        String caseNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal remaining
) {
}
