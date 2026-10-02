package com.kalima.backend.auth.repository;

import com.kalima.backend.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Non-locking read — used for logout and read-only checks outside the
     * rotation path.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Locking read used exclusively inside {@code RefreshTokenService.rotate()}.
     *
     * <p>Issues a {@code SELECT … FOR UPDATE} so that only ONE transaction can
     * hold the row at a time. The second concurrent request blocks until the
     * first transaction commits, then reads the committed state (which has
     * {@code usedAt} set) — closing the TOCTOU race window.
     *
     * <p>Lock timeout 5 s: if the first transaction does not release the lock
     * within 5 seconds a {@code PessimisticLockException} is thrown and the
     * caller sees a 500 (or a mapped error) rather than blocking indefinitely.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * Revoke every token in a family in a single UPDATE.
     *
     * <p>{@code clearAutomatically = true} flushes Hibernate's first-level
     * cache after the bulk UPDATE so that any entity loaded in the same
     * session reflects the new {@code revokedAt} value immediately.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now
             WHERE t.familyId = :familyId
               AND t.revokedAt IS NULL
            """)
    void revokeFamily(@Param("familyId") UUID familyId,
                      @Param("now")      Instant now);

    /**
     * Hard-delete tokens past their expiry.
     *
     * <p>{@code clearAutomatically = true} keeps the session cache coherent
     * after the bulk DELETE.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    void deleteExpiredTokens(@Param("cutoff") Instant cutoff);
}
