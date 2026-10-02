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
 * One statement line from the bank, matched to a transaction in our own books.
 *
 * <p>A reconciliation is a comparison, not a posting: both sides already exist in
 * the ledger, so matching records the agreement and leaves no journal entry. Only an
 * unmatched line can move money, and that is done explicitly through the unreconciled
 * bank account rather than by adjusting the real balance.
 */
@Entity
@Table(name = "bank_reconciliations")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class BankReconciliation {

    public enum Status { IN_PROGRESS, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false, updatable = false)
    private BankAccount bankAccount;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "statement_closing_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal statementClosingBalance = BigDecimal.ZERO;

    /**
     * Ledger balance carried forward to the statement date, including what is
     * already on the unreconciled-bank account.
     */
    @Column(name = "reconciled_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal reconciledBalance = BigDecimal.ZERO;

    @Column(name = "difference", nullable = false, precision = 19, scale = 2)
    private BigDecimal difference = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private Person completedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected BankReconciliation() {
    }

    /**
     * Instance factory for service code. The no-arg constructor stays protected so
     * JPA remains the only way to materialise an instance.
     */
    public static BankReconciliation of(Company company) {
        BankReconciliation instance = new BankReconciliation();
        instance.setCompany(company);
        return instance;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public BankAccount getBankAccount() { return bankAccount; }
    public void setBankAccount(BankAccount bankAccount) { this.bankAccount = bankAccount; }
    public LocalDate getStatementDate() { return statementDate; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }
    public BigDecimal getStatementClosingBalance() { return statementClosingBalance; }
    public void setStatementClosingBalance(BigDecimal statementClosingBalance) { this.statementClosingBalance = statementClosingBalance; }
    public BigDecimal getReconciledBalance() { return reconciledBalance; }
    public void setReconciledBalance(BigDecimal reconciledBalance) { this.reconciledBalance = reconciledBalance; }
    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Person getCompletedBy() { return completedBy; }
    public void setCompletedBy(Person completedBy) { this.completedBy = completedBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Person getCreatedBy() { return createdBy; }
    public void setCreatedBy(Person createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}