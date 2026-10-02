package com.kalima.backend.portal.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.util.UUID;

/**
 * Staff-side request to create/relink a client portal login
 * ({@link DomainEnums.SystemRole#CLIENT_USER}) and bind it to the firm's
 * {@code CLIENT} profile identified by {@code clientPersonId}. The portal
 * user authenticates through the normal JWT pipeline and receives no extra
 * powers; every portal read is scoped to the linked client inside the
 * portal layer, never through generic staff endpoints.
 */
public record PortalUserCreateRequest(
        /** The firm-side CLIENT profile this portal login is allowed to act as. */
        UUID clientPersonId,

        String fullName,

        String email,

        String phone
) {
}
