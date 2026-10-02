package com.kalima.backend.invoice.entity;

/** دورة حياة الفاتورة في وحدة التحصيل. */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    CANCELLED
}
