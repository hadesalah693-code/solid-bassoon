package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.AccountingSequence;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Gap-free, race-free document numbering.
 *
 * <p>The previous implementation incremented and then re-read the counter in two
 * separate statements, so two concurrent postings could read the same {@code max} and
 * emit the same document number (or, on the insert path, return a hard-coded
 * {@code 00001} that another request had already taken).
 *
 * <p>{@code INSERT … ON CONFLICT DO UPDATE … RETURNING} performs the read-modify-write
 * as one statement, so the database itself serialises concurrent callers and every
 * caller gets a distinct value.
 */
@Repository
public class AtomicSequenceDao {

    private final EntityManager entityManager;

    public AtomicSequenceDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Increments the counter for (company, scope, year) and returns the reserved
     * value. Must run in its own transaction so the reserved value is committed even
     * when the surrounding business transaction later rolls back — otherwise a retry
     * would re-issue a number that a failed entry never used, leaving a silent gap.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long nextValue(UUID companyId, String scope, int year) {
        Object value = entityManager.createNativeQuery("""
                        INSERT INTO accounting_sequences (id, company_id, scope, year, last_value)
                        VALUES (?1, ?2, ?3, ?4, 1)
                        ON CONFLICT (company_id, scope, year)
                        DO UPDATE SET last_value = accounting_sequences.last_value + 1
                        RETURNING last_value
                        """)
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, companyId)
                .setParameter(3, scope)
                .setParameter(4, year)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    /** Same contract for the invoice numbering table (per-company, per-year). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long nextInvoiceValue(UUID companyId, int year) {
        Object value = entityManager.createNativeQuery("""
                        INSERT INTO invoice_number_sequences (id, company_id, year, last_value)
                        VALUES (?1, ?2, ?3, 1)
                        ON CONFLICT (company_id, year)
                        DO UPDATE SET last_value = invoice_number_sequences.last_value + 1
                        RETURNING last_value
                        """)
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, companyId)
                .setParameter(3, year)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    /** Kept for readability at call sites. */
    public static String format(AccountingSequence.Scope scope, int year, long value) {
        String prefix = switch (scope) {
            case JOURNAL -> "JE";
            case EXPENSE -> "EXP";
            case RECEIPT -> "RCP";
            case VENDOR_BILL -> "BILL";
            case PAYMENT -> "PAY";
            case RETAINER -> "RET";
            case CREDIT_NOTE -> "CRN";
            case DEBIT_NOTE -> "DBN";
            case REFUND -> "RFD";
            case TIME_ENTRY -> "TIME";
        };
        return prefix + "-" + year + "-" + String.format("%05d", value);
    }
}
