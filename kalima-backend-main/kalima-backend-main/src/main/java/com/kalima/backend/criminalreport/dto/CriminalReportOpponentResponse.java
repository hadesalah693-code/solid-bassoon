package com.kalima.backend.criminalreport.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.util.UUID;

public record CriminalReportOpponentResponse(
        UUID id,
        String name,
        DomainEnums.CriminalReportPartyCapacity capacity
) {
}
