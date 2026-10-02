package com.kalima.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted record of a single refresh token slot.
 *
 * <p>Only the SHA-256 hex digest of the raw token is stored here; the raw
 * token lives exclusively inside the client's {@code HttpOnly} cookie.
 *
 * <p>Token Rotation / Theft Detection:
 * <ul>
 *   <li>All tokens that share the same {@code familyId} originate from the
 *       same login session.</li>
 *   <li>When a token is rotated, {@code usedAt} is set on the current row
 *       and a new row (same {@code familyId}) is inserted.</li>
 *   <li>If an already-used token is presented, the whole family is revoked
 *       immediately — this signals a possible token theft.</li>
 * </ul>
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /** Groups all rotation-linked tokens from a single login session. */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    /** SHA-256 hex of the opaque raw token. Never store the raw token here. */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /** Set when this slot is consumed during a rotation; NULL means still valid. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Set on explicit logout or on family-wide revocation after reuse detection. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {}

    private RefreshToken(Builder b) {
        this.personId   = b.personId;
        this.companyId  = b.companyId;
        this.familyId   = b.familyId;
        this.tokenHash  = b.tokenHash;
        this.expiresAt  = b.expiresAt;
        this.createdAt  = Instant.now();
    }

    // ------------------------------------------------------------------ factory

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID    personId;
        private UUID    companyId;
        private UUID    familyId;
        private String  tokenHash;
        private Instant expiresAt;

        private Builder() {}

        public Builder personId(UUID v)   { this.personId  = v; return this; }
        public Builder companyId(UUID v)  { this.companyId = v; return this; }
        public Builder familyId(UUID v)   { this.familyId  = v; return this; }
        public Builder tokenHash(String v){ this.tokenHash  = v; return this; }
        public Builder expiresAt(Instant v){ this.expiresAt = v; return this; }

        public RefreshToken build() {
            return new RefreshToken(this);
        }
    }

    // ------------------------------------------------------------------ state mutators

    /** Mark this slot as consumed (called during token rotation). */
    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    /** Mark this slot as revoked (logout or theft-detected family revocation). */
    public void markRevoked(Instant now) {
        this.revokedAt = now;
    }

    // ------------------------------------------------------------------ queries

    public boolean isActive(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    // ------------------------------------------------------------------ accessors

    public UUID    getId()        { return id; }
    public UUID    getPersonId()  { return personId; }
    public UUID    getCompanyId() { return companyId; }
    public UUID    getFamilyId()  { return familyId; }
    public String  getTokenHash() { return tokenHash; }
    public Instant getUsedAt()    { return usedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
