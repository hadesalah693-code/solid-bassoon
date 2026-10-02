package com.kalima.backend.mandate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Response for a mandate payment row. */
public record MandatePaymentResponse(
        UUID id,
        BigDecimal amount,
        LocalDate paymentDate,
        String method,
        String notes,
        String recordedByEmail,
        LocalDate createdAt
) {
}
