package com.kalima.backend.accounting.entity;

import com.kalima.backend.domain.entity.Company;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounting_sequences")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class AccountingSequence {

    /** Mirrors the {@code chk_accounting_sequences_scope} CHECK maintained by Flyway. */
    public enum Scope {
        JOURNAL, EXPENSE, RECEIPT,
        VENDOR_BILL, PAYMENT, RETAINER,
        CREDIT_NOTE, DEBIT_NOTE, REFUND,
        /** Time-entry numbering; allowed by chk_accounting_sequences_scope since V77. */
        TIME_ENTRY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private Scope scope;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "last_value", nullable = false)
    private long lastValue = 0L;

    @PrePersist
    @PreUpdate
    void touch() {
        if (company == null) throw new IllegalStateException("company is required");
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public long getLastValue() { return lastValue; }
    public void setLastValue(long lastValue) { this.lastValue = lastValue; }
}
