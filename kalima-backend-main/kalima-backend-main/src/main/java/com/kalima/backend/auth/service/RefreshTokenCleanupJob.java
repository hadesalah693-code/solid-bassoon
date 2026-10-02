package com.kalima.backend.auth.service;

import com.kalima.backend.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Periodic hard-delete of expired refresh-token rows.
 *
 * <p>Expired tokens are functionally dead (they fail {@code isActive()} checks)
 * but accumulate in the table over time. This job purges them nightly so the
 * table stays bounded even under heavy login traffic.
 *
 * <p>Schedule: 03:00 server time daily — chosen to be off-peak for most
 * deployments. Override via {@code KALIMA_AUTH_CLEANUP_CRON} if needed.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository repo;

    public RefreshTokenCleanupJob(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    @Scheduled(cron = "${kalima.auth.refresh.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpired() {
        Instant cutoff = Instant.now();
        repo.deleteExpiredTokens(cutoff);
        log.info("Refresh token cleanup completed (cutoff={})", cutoff);
    }
}
