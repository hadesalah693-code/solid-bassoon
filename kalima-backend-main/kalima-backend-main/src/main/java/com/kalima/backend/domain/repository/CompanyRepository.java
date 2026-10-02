package com.kalima.backend.domain.repository;

import com.kalima.backend.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Note on tenant safety: the tenant id <em>is</em> the company id (JWT carries
 * {@code companyId}, and {@code companies.id} is the primary key), so
 * {@link JpaRepository#findById(Object)} on this repository is inherently scoped to
 * one company — a lookup by a different id addresses a different tenant's row rather
 * than leaking the current one.
 */
public interface CompanyRepository extends JpaRepository<Company, UUID> {
}
