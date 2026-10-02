package com.kalima.backend.dispute.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.util.UUID;

public record DisputeTeamMemberResponse(
        UUID id,
        DomainEnums.DisputeTeamRole role,
        PartySummary person
) {
}
