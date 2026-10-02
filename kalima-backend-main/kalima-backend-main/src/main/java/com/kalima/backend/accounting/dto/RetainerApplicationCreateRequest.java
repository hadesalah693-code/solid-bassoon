package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.RetainerApplication.TargetType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RetainerApplicationCreateRequest(
        @NotNull UUID retainerId,
        @NotNull @DecimalMin("0.01") BigDecimal appliedAmount,
        @NotNull LocalDate appliedDate,
        @NotNull TargetType targetType,
        @NotNull UUID targetId,
        String notes
) {
}