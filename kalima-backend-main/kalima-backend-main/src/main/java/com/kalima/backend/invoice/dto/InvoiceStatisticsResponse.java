package com.kalima.backend.invoice.dto;

import java.math.BigDecimal;

/** إحصائيات مجمّعة لبطاقات التحصيل في رأس صفحة الفواتير. */
public record InvoiceStatisticsResponse(
        int total,
        int paid,
        BigDecimal totalBilled,
        BigDecimal totalCollected,
        BigDecimal totalRemaining
) {
}
