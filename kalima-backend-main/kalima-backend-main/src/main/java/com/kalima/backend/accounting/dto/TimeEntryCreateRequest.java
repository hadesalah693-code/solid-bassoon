package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TimeEntryCreateRequest(
        UUID caseId,
        UUID lawyerId,
        LocalDate workDate,
        @NotNull @Min(1) Integer durationMinutes,
        @Size(max = 500) String description,
        Boolean billable,
        @DecimalMin("0") BigDecimal hourlyRate) {
}