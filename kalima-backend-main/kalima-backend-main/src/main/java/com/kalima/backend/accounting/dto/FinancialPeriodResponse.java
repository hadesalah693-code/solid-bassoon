package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.FinancialPeriod.Status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialPeriodResponse(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Status status,
        Instant closedAt,
        String closedBy,
        long entryCount) {
}
