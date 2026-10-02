package com.kalima.backend.criminalreport.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.util.UUID;

public record CriminalReportClientResponse(
        UUID id,
        PartySummary client,
        DomainEnums.CriminalReportPartyCapacity capacity
) {
}
