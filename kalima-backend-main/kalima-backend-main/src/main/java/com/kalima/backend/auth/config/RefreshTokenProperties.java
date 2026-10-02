package com.kalima.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the refresh-token HttpOnly cookie.
 *
 * <pre>
 * kalima.auth.refresh.ttl-days=30
 * kalima.auth.refresh.cookie-name=refresh_token
 * kalima.auth.refresh.cookie-secure=true
 * kalima.auth.refresh.cookie-same-site=Strict
 * </pre>
 *
 * All values have safe production-ready defaults; override via environment or
 * application-dev.properties for local development.
 *
 * <p><b>Cross-site deployments</b> (frontend and backend on different
 * registrable domains — e.g. a Vercel-hosted SPA calling a Railway-hosted
 * API): browsers never send {@code SameSite=Strict} or {@code Lax} cookies on
 * cross-site fetch/XHR requests, which silently breaks the refresh-token
 * flow. Set {@code KALIMA_AUTH_REFRESH_COOKIE_SAME_SITE=None} in that case
 * (requires {@code cookieSecure=true}, which is already the default). If the
 * frontend and backend instead share a registrable domain (e.g.
 * {@code app.example.com} and {@code api.example.com}), {@code Strict} works
 * fine and is the stronger CSRF defense.
 */
@ConfigurationProperties(prefix = "kalima.auth.refresh")
public class RefreshTokenProperties {

    /** Lifetime of a refresh token in days. Default 30 days. */
    private int ttlDays = 30;

    /** Name of the HttpOnly cookie that carries the raw opaque token. */
    private String cookieName = "refresh_token";

    /**
     * When {@code true} the {@code Secure} attribute is added to the cookie so
     * it is only sent over HTTPS. Set to {@code false} in local dev profiles.
     */
    private boolean cookieSecure = true;

    /**
     * {@code SameSite} attribute: {@code Strict}, {@code Lax}, or {@code None}.
     * Must be {@code None} for cross-site (different registrable domain)
     * frontend/backend deployments — see class Javadoc. Requires
     * {@code cookieSecure=true} when set to {@code None} (browsers reject
     * {@code SameSite=None} cookies without {@code Secure}).
     */
    private String cookieSameSite = "Strict";

    public int getTtlDays() {
        return ttlDays;
    }

    public void setTtlDays(int ttlDays) {
        this.ttlDays = ttlDays;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }
}
