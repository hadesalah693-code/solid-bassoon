package com.kalima.backend.laborcomplaint.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.util.UUID;

public record LaborComplaintOpponentResponse(
        UUID id,
        String name,
        DomainEnums.LaborComplaintPartyCapacity capacity
) {
}
