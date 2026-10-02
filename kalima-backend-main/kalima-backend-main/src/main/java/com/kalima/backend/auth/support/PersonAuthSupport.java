package com.kalima.backend.auth.support;

import com.kalima.backend.domain.entity.Person;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Centralizes rules for "who is allowed to act as this {@link Person}" and how
 * {@code ROLE_*} strings are built. Used by both password-based login
 * ({@link com.kalima.backend.auth.service.CustomUserDetailsService}) and
 * JWT-backed requests ({@link com.kalima.backend.auth.jwt.JwtAuthenticationFilter}).
 */
public final class PersonAuthSupport {

    private PersonAuthSupport() {
    }

    /**
     * Same business rules as password login: active account, credentials present,
     * not archived, company present and active. Clients without passwords never pass.
     */
    public static boolean isAllowedAuthenticatedSession(Person person) {
        if (person.getPasswordHash() == null || !person.isActive()) {
            return false;
        }
        if (person.getArchivedAt() != null) {
            return false;
        }
        return person.getCompany() != null && person.getCompany().isActive();
    }

    public static List<SimpleGrantedAuthority> roleAuthorities(Person person) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + person.getSystemRole().name()));
    }
}
