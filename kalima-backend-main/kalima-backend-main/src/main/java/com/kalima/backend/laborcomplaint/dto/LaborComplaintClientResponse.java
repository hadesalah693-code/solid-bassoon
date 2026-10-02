package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.util.UUID;

public record LaborComplaintClientResponse(
        UUID id,
        PartySummary client,
        DomainEnums.LaborComplaintPartyCapacity capacity
) {
}
