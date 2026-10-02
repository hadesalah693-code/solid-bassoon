package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.RetainerApplication.TargetType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RetainerApplicationResponse(
        UUID id, UUID companyId, UUID retainerId, BigDecimal appliedAmount,
        LocalDate appliedDate, UUID appliedBy, String appliedByName,
        TargetType targetType, UUID targetId, String targetReference,
        UUID journalEntryId, String notes, String createdAt
) {
}