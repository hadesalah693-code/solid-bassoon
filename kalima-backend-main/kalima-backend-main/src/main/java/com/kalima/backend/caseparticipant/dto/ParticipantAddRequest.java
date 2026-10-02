package com.kalima.backend.caseparticipant.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ParticipantAddRequest(
        @NotNull(message = "personId is required")
        UUID personId,

        @NotNull(message = "role is required")
        DomainEnums.CaseParticipantRole role,

        String notes
) {
}
