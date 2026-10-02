package com.kalima.backend.domain.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Request-scoped holder for the current tenant's company id.
 *
 * <p>Filled by {@code JwtAuthenticationFilter} right after the JWT is
 * validated, and cleared in a {@code finally} block so the value never leaks
 * into another request on the same servlet thread.
 *
 * <p>Read by:
 * <ul>
 *   <li>{@link TenantFilterAspect} — to activate the Hibernate tenant filter.</li>
 *   <li>Entity {@code @PrePersist} hooks — to auto-inject {@code company} on
 *       new rows when the service layer hasn't set it explicitly.</li>
 * </ul>
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID companyId) {
        CURRENT.set(companyId);
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * @throws IllegalStateException if no tenant is set on the current thread.
     *         Use this from code paths that MUST run inside a tenant context
     *         (e.g. persistence of tenant-scoped entities).
     */
    public static UUID getRequired() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException(
                    "No tenant context set on current thread. "
                    + "This usually means a tenant-scoped operation ran outside an authenticated request.");
        }
        return id;
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
