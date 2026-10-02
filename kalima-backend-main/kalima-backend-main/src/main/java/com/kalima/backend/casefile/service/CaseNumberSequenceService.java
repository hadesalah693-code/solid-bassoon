package com.kalima.backend.casefile.service;

import com.kalima.backend.domain.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
public class CaseNumberSequenceService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public String nextNumber() {
        int year = Year.now().getValue();
        Number sequence = (Number) entityManager.createNativeQuery("""
                                INSERT INTO case_number_counters (company_id, case_year, last_value)
                                SELECT :companyId, :caseYear,
                                             COALESCE(MAX(substring(c.case_number FROM 11)::integer), 0) + 1
                                FROM cases c
                                WHERE c.company_id = :companyId
                                    AND c.case_number ~ ('^CASE-' || :caseYear || '-[0-9]{5}$')
                ON CONFLICT (company_id, case_year)
                                DO UPDATE SET last_value = GREATEST(
                                        case_number_counters.last_value + 1, EXCLUDED.last_value)
                RETURNING last_value
                """)
                .setParameter("companyId", TenantContext.getRequired())
                .setParameter("caseYear", year)
                .getSingleResult();
        return "CASE-%d-%05d".formatted(year, sequence.intValue());
    }
}