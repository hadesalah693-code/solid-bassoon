package com.kalima.backend.circulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CirculationCreateRequest(
        @NotNull UUID caseId,
        @NotNull UUID fromPersonId,
        @NotNull UUID toPersonId,
        LocalDateTime dueBackAt,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 10000) String notes
) {
}