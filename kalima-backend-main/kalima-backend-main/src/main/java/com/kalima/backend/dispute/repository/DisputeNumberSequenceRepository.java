package com.kalima.backend.dispute.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Allocates per-(company, calendar-year) dispute sequence numbers using PostgreSQL
 * {@code INSERT ... ON CONFLICT DO UPDATE}, which locks the counter row and avoids
 * duplicate numbers under concurrent creates.
 */
@Repository
public class DisputeNumberSequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public DisputeNumberSequenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Reserves and returns the next 1-based sequence for the given company and year.
     * Formatted dispute numbers (e.g. DIS-2026-0001) are built in the service layer.
     */
    public int allocateNextSequence(UUID companyId, int year) {
        Integer seq = jdbcTemplate.queryForObject(
                """
                        INSERT INTO dispute_number_counters (company_id, year, next_number, created_at, updated_at)
                        VALUES (?::uuid, ?, 2, NOW(), NOW())
                        ON CONFLICT (company_id, year) DO UPDATE SET
                            next_number = dispute_number_counters.next_number + 1,
                            updated_at = NOW()
                        RETURNING next_number - 1
                        """,
                Integer.class,
                companyId.toString(),
                year);
        if (seq == null) {
            throw new IllegalStateException("Failed to allocate dispute sequence");
        }
        return seq;
    }
}
