package com.kalima.backend.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RetainerResponse(
        UUID id, UUID companyId, UUID clientId, String clientNameAr,
        BigDecimal amount, String currency, RetainerStatus status,
        LocalDate receivedDate, UUID receivedBy, String receivedByName,
        String reference, String notes, BigDecimal appliedAmount,
        BigDecimal remainingAmount, String createdAt
) {
    public enum RetainerStatus { ACTIVE, FULLY_APPLIED, REFUNDED, CANCELLED }
}