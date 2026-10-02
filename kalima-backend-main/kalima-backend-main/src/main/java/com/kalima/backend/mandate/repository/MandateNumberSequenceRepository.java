package com.kalima.backend.mandate.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Allocates the sequential mandate number per company and year. */
@Repository
public class MandateNumberSequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public MandateNumberSequenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int allocateNextSequence(UUID companyId, int year) {
        Integer seq = jdbcTemplate.queryForObject(
                """
                        INSERT INTO mandate_number_counters (company_id, year, next_number, created_at, updated_at)
                        VALUES (?::uuid, ?, 2, NOW(), NOW())
                        ON CONFLICT (company_id, year) DO UPDATE SET
                            next_number = mandate_number_counters.next_number + 1,
                            updated_at = NOW()
                        RETURNING next_number - 1
                        """,
                Integer.class,
                companyId.toString(),
                year);
        if (seq == null) {
            throw new IllegalStateException("Failed to allocate mandate sequence");
        }
        return seq;
    }
}
