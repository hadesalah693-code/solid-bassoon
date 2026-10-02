package com.kalima.backend.delegation.settings.dto;

/**
 * Patch payload: {@code null} fields are left unchanged. The linked person
 * cannot be changed after creation — delete and re-add instead.
 */
public record DelegationOfficeAttorneyUpdateRequest(
        Boolean active,
        Boolean isDefault
) {
}
