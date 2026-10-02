package com.kalima.backend.auth.service;

import com.kalima.backend.auth.config.RefreshTokenProperties;
import com.kalima.backend.auth.entity.RefreshToken;
import com.kalima.backend.auth.repository.RefreshTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

/**
 * All refresh-token lifecycle operations.
 *
 * <p>Raw tokens are 32-byte CSPRNG values formatted as hex strings.  Only
 * their SHA-256 hash is persisted; the raw value exists only inside the
 * client's HttpOnly cookie.
 *
 * <p>Token Rotation contract:
 * <ol>
 *   <li>Each successful rotation marks the current slot {@code usedAt} and
 *       inserts a new slot in the same {@code familyId}.</li>
 *   <li>Presenting a <em>used</em> token is the theft signal: the entire
 *       family is revoked immediately and a 401 is returned.</li>
 * </ol>
 */
@Service
@Transactional
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repo;
    private final RefreshTokenProperties props;

    public RefreshTokenService(RefreshTokenRepository repo, RefreshTokenProperties props) {
        this.repo  = repo;
        this.props = props;
    }

    // ─── public API ──────────────────────────────────────────────────────────

    /**
     * Issue a brand-new refresh token in a new family.
     * Called once per successful login.
     */
    public IssuedToken issue(UUID personId, UUID companyId) {
        return issueInFamily(personId, companyId, UUID.randomUUID());
    }

    /**
     * Validate, consume the current slot, and emit a successor slot in the
     * same family.
     *
     * <h3>Race-condition safety (TOCTOU closed)</h3>
     * <p>Uses {@code findByTokenHashForUpdate} which issues a
     * {@code SELECT … FOR UPDATE} in PostgreSQL.  When two concurrent
     * requests arrive with the same raw token:
     * <ol>
     *   <li>T1 acquires the row-level lock, checks {@code usedAt == null},
     *       marks the row used, issues a successor, and commits.</li>
     *   <li>T2 blocks until T1 commits, then reads the row again (isolation
     *       level {@code READ_COMMITTED}): it now sees {@code usedAt != null},
     *       detects reuse, revokes the family, and returns 401.</li>
     * </ol>
     * Result: <strong>exactly one</strong> successor is ever issued for a
     * given token slot — even under concurrent load.
     *
     * <h3>Rollback behaviour</h3>
     * <p>Annotated with {@code noRollbackFor = ResponseStatusException.class}
     * so that when a 401 is thrown (theft-detection path), the transaction
     * <em>commits</em> rather than rolling back.  This guarantees the
     * {@code revokeFamily} bulk-UPDATE is durable: the attacker's reuse
     * cannot silently undo the family revocation through a rollback.
     *
     * @param rawToken the opaque value from the HttpOnly cookie
     * @return rotation result containing the new raw token and the owner's IDs
     * @throws ResponseStatusException 401 when the token is invalid, used, or expired
     */
    @Transactional(propagation = Propagation.REQUIRED, noRollbackFor = ResponseStatusException.class)
    public RotationResult rotate(String rawToken) {
        Instant now = Instant.now();

        // SELECT … FOR UPDATE: serialises concurrent rotations on the same slot.
        RefreshToken existing = repo.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> unauthorized("Invalid refresh token"));

        if (existing.getUsedAt() != null) {
            // Reuse detected: a consumed token was presented again.
            // Revoke the entire family to neutralise any successor the
            // legitimate client may hold.  noRollbackFor ensures this UPDATE
            // commits even though we throw immediately after.
            repo.revokeFamily(existing.getFamilyId(), now);
            throw unauthorized("Refresh token reuse detected; all sessions in this chain have been revoked");
        }

        if (!existing.isActive(now)) {
            throw unauthorized("Refresh token expired or revoked");
        }

        existing.markUsed(now);
        repo.save(existing);

        IssuedToken next = issueInFamily(existing.getPersonId(), existing.getCompanyId(), existing.getFamilyId());
        return new RotationResult(next, existing.getPersonId(), existing.getCompanyId());
    }

    /**
     * Revoke the entire family that owns the given raw token.
     * Silent no-op when the token is unknown — idempotent logout.
     */
    public void revokeByRawToken(String rawToken) {
        repo.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repo.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    // ─── cookie helpers ──────────────────────────────────────────────────────

    /**
     * Build a {@code Set-Cookie} header that plants the raw token in the
     * browser with HttpOnly, Secure (configurable), SameSite (configurable —
     * see {@link RefreshTokenProperties}). Path is restricted to
     * {@code /api/auth} so the cookie is not sent on every API request — only
     * on the auth endpoints that need it.
     */
    public ResponseCookie buildCookie(String rawToken, Instant expiresAt) {
        return ResponseCookie.from(props.getCookieName(), rawToken)
                .httpOnly(true)
                .secure(props.isCookieSecure())
                .sameSite(props.getCookieSameSite())
                .path("/api/auth")
                .maxAge(Duration.between(Instant.now(), expiresAt))
                .build();
    }

    /** Cookie name from configuration — used by the controller to read the cookie. */
    public String cookieName() {
        return props.getCookieName();
    }

    /**
     * Build a zero-maxAge cookie to clear the refresh token from the browser.
     */
    public ResponseCookie clearCookie() {
        return ResponseCookie.from(props.getCookieName(), "")
                .httpOnly(true)
                .secure(props.isCookieSecure())
                .sameSite(props.getCookieSameSite())
                .path("/api/auth")
                .maxAge(0)
                .build();
    }

    // ─── internal ────────────────────────────────────────────────────────────

    private IssuedToken issueInFamily(UUID personId, UUID companyId, UUID familyId) {
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(props.getTtlDays(), ChronoUnit.DAYS);

        repo.save(RefreshToken.builder()
                .personId(personId)
                .companyId(companyId)
                .familyId(familyId)
                .tokenHash(hash(rawToken))
                .expiresAt(expiresAt)
                .build());

        return new IssuedToken(rawToken, expiresAt);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Package-private for unit testing. */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    // ─── result types ─────────────────────────────────────────────────────────

    /** Raw token + its expiry — used to build the cookie. */
    public record IssuedToken(String rawToken, Instant expiresAt) {}

    /** Returned from {@link #rotate}: new token slot + identity of the owner. */
    public record RotationResult(IssuedToken issuedToken, UUID personId, UUID companyId) {}
}
