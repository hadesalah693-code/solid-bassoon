package com.kalima.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * In-process login throttling. Limits apply to {@code POST /api/auth/login} only.
 * Production clusters should add edge rate limiting; this is defense-in-depth.
 */
@ConfigurationProperties(prefix = "kalima.auth.login")
public class LoginRateLimitProperties {

    /**
     * When false, all login requests pass (e.g. local debugging).
     */
    private boolean rateLimitEnabled = true;

    /** Max login attempts per client IP per window. */
    private int maxAttemptsPerWindowByIp = 20;

    /** Max login attempts per email (normalized lower-case) per window. */
    private int maxAttemptsPerWindowByEmail = 10;

    /** Sliding / fixed window length. */
    private int windowMinutes = 15;

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public int getMaxAttemptsPerWindowByIp() {
        return maxAttemptsPerWindowByIp;
    }

    public void setMaxAttemptsPerWindowByIp(int maxAttemptsPerWindowByIp) {
        this.maxAttemptsPerWindowByIp = maxAttemptsPerWindowByIp;
    }

    public int getMaxAttemptsPerWindowByEmail() {
        return maxAttemptsPerWindowByEmail;
    }

    public void setMaxAttemptsPerWindowByEmail(int maxAttemptsPerWindowByEmail) {
        this.maxAttemptsPerWindowByEmail = maxAttemptsPerWindowByEmail;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }
}
