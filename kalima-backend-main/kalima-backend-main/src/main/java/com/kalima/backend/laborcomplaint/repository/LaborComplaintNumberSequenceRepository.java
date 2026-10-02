package com.kalima.backend.laborcomplaint.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class LaborComplaintNumberSequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public LaborComplaintNumberSequenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int allocateNextSequence(UUID companyId, int year) {
        Integer seq = jdbcTemplate.queryForObject(
                """
                        INSERT INTO labor_complaint_number_counters (company_id, year, next_number, created_at, updated_at)
                        VALUES (?::uuid, ?, 2, NOW(), NOW())
                        ON CONFLICT (company_id, year) DO UPDATE SET
                            next_number = labor_complaint_number_counters.next_number + 1,
                            updated_at = NOW()
                        RETURNING next_number - 1
                        """,
                Integer.class,
                companyId.toString(),
                year);
        if (seq == null) {
            throw new IllegalStateException("Failed to allocate labor complaint sequence");
        }
        return seq;
    }
}
