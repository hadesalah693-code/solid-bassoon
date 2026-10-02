package com.kalima.backend.accounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TaxRateResponse(
        UUID id,
        String nameAr,
        BigDecimal rate,
        UUID accountId,
        boolean active,
        boolean defaultRate) {
}
