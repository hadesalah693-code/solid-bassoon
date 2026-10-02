package com.kalima.backend.casefile.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.util.UUID;

/**
 * Lightweight read-only projection of a Person that is referenced by a case
 * (client, assigned lawyer, creator). Keeps case responses from dragging the
 * full PersonResponse payload and avoids N+1 concerns on list endpoints.
 */
public record PartySummary(
        UUID id,
        String fullName,
        DomainEnums.SystemRole systemRole
) {
}
