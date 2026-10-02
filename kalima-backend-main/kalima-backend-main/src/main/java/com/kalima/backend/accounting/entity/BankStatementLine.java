package com.kalima.backend.accounting.entity;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A line as the bank reported it, plus whichever of our transactions it corresponds to.
 *
 * <p>{@code matchedType} is a closed set on purpose: it points at documents this
 * module owns (receipts, vendor payments, transfers, expenses) rather than accepting
 * a free-text reference, so a match can always be traced back to a posted journal.
 */
@Entity
@Table(name = "bank_statement_lines")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class BankStatementLine {

    public enum MatchType { RECEIPT, PAYMENT, TRANSFER, EXPENSE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false, updatable = false)
    private BankAccount bankAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id")
    private BankReconciliation reconciliation;

    @Column(name = "line_date", nullable = false)
    private LocalDate lineDate;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "reference", length = 100)
    private String reference;

    /** Signed as the bank sees it: money in positive, money out negative. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "matched_type", length = 25)
    private MatchType matchedType;

    @Column(name = "matched_id")
    private UUID matchedId;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected BankStatementLine() {
    }

    /**
     * Instance factory for service code. The no-arg constructor stays protected so
     * JPA remains the only way to materialise an instance.
     */
    public static BankStatementLine of(Company company) {
        BankStatementLine instance = new BankStatementLine();
        instance.setCompany(company);
        return instance;
    }

    public boolean isMatched() { return matchedId != null; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public BankAccount getBankAccount() { return bankAccount; }
    public void setBankAccount(BankAccount bankAccount) { this.bankAccount = bankAccount; }
    public BankReconciliation getReconciliation() { return reconciliation; }
    public void setReconciliation(BankReconciliation reconciliation) { this.reconciliation = reconciliation; }
    public LocalDate getLineDate() { return lineDate; }
    public void setLineDate(LocalDate lineDate) { this.lineDate = lineDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public MatchType getMatchedType() { return matchedType; }
    public void setMatchedType(MatchType matchedType) { this.matchedType = matchedType; }
    public UUID getMatchedId() { return matchedId; }
    public void setMatchedId(UUID matchedId) { this.matchedId = matchedId; }
    public Instant getMatchedAt() { return matchedAt; }
    public void setMatchedAt(Instant matchedAt) { this.matchedAt = matchedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Person getCreatedBy() { return createdBy; }
    public void setCreatedBy(Person createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}