package com.kalima.backend.mandate.dto;

import java.math.BigDecimal;

/** Aggregated statistics for the mandates header cards. */
public record MandateStatisticsResponse(
        long total,
        long today,
        long upcoming,
        long completed,
        long cancelled,
        BigDecimal totalFees,
        BigDecimal totalPaid,
        BigDecimal totalRemaining
) {
}
