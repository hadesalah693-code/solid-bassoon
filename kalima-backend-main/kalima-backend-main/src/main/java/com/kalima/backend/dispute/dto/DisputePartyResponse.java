package com.kalima.backend.dispute.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.util.UUID;

public record DisputePartyResponse(
        UUID id,
        UUID disputeId,
        DomainEnums.DisputePartyRole role,
        PartySummary person,
        String displayName,
        String phone,
        String notes,
        int sortOrder
) {
}
