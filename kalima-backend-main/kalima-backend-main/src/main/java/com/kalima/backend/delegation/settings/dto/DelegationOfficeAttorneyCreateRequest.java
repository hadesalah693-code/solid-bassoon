package com.kalima.backend.delegation.settings.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload for registering a staff member as an office attorney.
 *
 * <p>{@code active} defaults to {@code true} and {@code isDefault} defaults
 * to {@code false} when omitted.
 */
public record DelegationOfficeAttorneyCreateRequest(
        @NotNull(message = "personId is required")
        UUID personId,

        Boolean active,

        Boolean isDefault
) {
}
