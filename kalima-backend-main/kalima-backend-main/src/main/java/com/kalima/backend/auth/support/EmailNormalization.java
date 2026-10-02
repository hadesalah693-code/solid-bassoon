package com.kalima.backend.auth.support;

import java.util.Locale;

/**
 * Canonical form for person login emails: trim + US ASCII case fold via
 * {@link String#toLowerCase(Locale)} with {@link Locale#ROOT} (stable for
 * email local-parts, avoids locale-specific surprises).
 */
public final class EmailNormalization {

    private EmailNormalization() {
    }

    /**
     * @return trimmed lower-case email, or {@code null} if null/blank/whitespace-only
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.toLowerCase(Locale.ROOT);
    }
}
