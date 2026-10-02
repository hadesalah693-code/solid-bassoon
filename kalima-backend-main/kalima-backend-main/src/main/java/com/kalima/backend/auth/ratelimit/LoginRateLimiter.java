package com.kalima.backend.auth.ratelimit;

import com.kalima.backend.auth.config.LoginRateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed-window counters per client IP and per email. A login attempt is allowed
 * only if <em>both</em> windows have capacity; both are incremented together
 * (see class lock) so partial double-charging cannot happen.
 */
@Component
public class LoginRateLimiter {

    private final LoginRateLimitProperties props;
    private final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<String, Window> byIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> byEmail = new ConcurrentHashMap<>();

    public LoginRateLimiter(LoginRateLimitProperties props) {
        this.props = props;
    }

    /**
     * @return true if the attempt is within limits; false if rate limit exceeded
     */
    public boolean allowLoginAttempt(HttpServletRequest request, String email) {
        if (!props.isRateLimitEnabled()) {
            return true;
        }
        String ipKey = "ip:" + resolveClientIp(request);
        String emailKey = "em:" + email.toLowerCase(Locale.ROOT).trim();
        long windowMs = props.getWindowMinutes() * 60_000L;
        int maxIp = Math.max(1, props.getMaxAttemptsPerWindowByIp());
        int maxEmail = Math.max(1, props.getMaxAttemptsPerWindowByEmail());
        long now = System.currentTimeMillis();

        lock.lock();
        try {
            Window wIp = byIp.computeIfAbsent(ipKey, k -> new Window());
            Window wEm = byEmail.computeIfAbsent(emailKey, k -> new Window());
            if (!wIp.hasCapacity(now, windowMs, maxIp) || !wEm.hasCapacity(now, windowMs, maxEmail)) {
                return false;
            }
            wIp.record(now, windowMs);
            wEm.record(now, windowMs);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears in-memory counters. Intended for integration tests; not exposed via HTTP.
     */
    public void resetInMemoryState() {
        lock.lock();
        try {
            byIp.clear();
            byEmail.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Client IP: first address in {@code X-Forwarded-For} when present, else remote address.
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = comma > 0 ? xff.substring(0, comma) : xff;
            return first.trim();
        }
        return request.getRemoteAddr();
    }

    static final class Window {
        long windowStartEpochMs;
        int count;

        boolean hasCapacity(long now, long windowMs, int max) {
            rollWindowIfNeeded(now, windowMs);
            return count < max;
        }

        void record(long now, long windowMs) {
            rollWindowIfNeeded(now, windowMs);
            count++;
        }

        private void rollWindowIfNeeded(long now, long windowMs) {
            if (windowStartEpochMs == 0) {
                windowStartEpochMs = now;
                count = 0;
                return;
            }
            if (now - windowStartEpochMs >= windowMs) {
                windowStartEpochMs = now;
                count = 0;
            }
        }
    }
}
