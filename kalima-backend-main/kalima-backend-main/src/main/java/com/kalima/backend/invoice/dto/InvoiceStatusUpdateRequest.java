package com.kalima.backend.invoice.dto;

import com.kalima.backend.invoice.entity.InvoiceStatus;

import jakarta.validation.constraints.NotNull;

/** تحديث حالة الفاتورة (إصدار/إلغاء/تحديد متأخرة…). */
public record InvoiceStatusUpdateRequest(
        @NotNull(message = "status is required")
        InvoiceStatus status
) {
}
