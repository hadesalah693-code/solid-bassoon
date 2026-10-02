package com.kalima.backend.casefile.entity;

import com.kalima.backend.domain.entity.BaseAuditEntity;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A legal case file (matter).
 */
@Entity
@Table(name = "cases")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class CaseFile extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    // Uniqueness is composite (company_id, case_number); see V9 migration.
    @Column(name = "case_number", nullable = false, length = 100)
    private String caseNumber;

    @Column(name = "court_case_number", length = 100)
    private String courtCaseNumber;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DomainEnums.CaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private DomainEnums.CasePriority priority = DomainEnums.CasePriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_case_id")
    private CaseFile parentCase;

    @Column(name = "case_type", length = 100)
    private String caseType;

    /** Display name of the primary court (case-level, not a specific session). */
    @Column(name = "court_name", length = 200)
    private String courtName;

    /** Judicial circuit / chamber label for the file (مثلاً: الدائرة). */
    @Column(name = "court_circuit", length = 100)
    private String courtCircuit;

    /** Current stage of the proceedings (e.g. استئناف، أول درجة). */
    @Column(name = "procedure_stage", length = 100)
    private String procedureStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_path_type", length = 50)
    private DomainEnums.DisputePathType disputePathType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "close_date")
    private LocalDate closeDate;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Person client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_lawyer_id")
    private Person assignedLawyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Person createdBy;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException(
                    "CaseFile.company must be set before persist.");
        }
    }

    // --- explicit getters/setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public String getCaseNumber() { return caseNumber; }
    public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }

    public String getCourtCaseNumber() { return courtCaseNumber; }
    public void setCourtCaseNumber(String courtCaseNumber) { this.courtCaseNumber = courtCaseNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public DomainEnums.CaseStatus getStatus() { return status; }
    public void setStatus(DomainEnums.CaseStatus status) { this.status = status; }

    public DomainEnums.CasePriority getPriority() { return priority; }
    public void setPriority(DomainEnums.CasePriority priority) { this.priority = priority; }

    public CaseFile getParentCase() { return parentCase; }
    public void setParentCase(CaseFile parentCase) { this.parentCase = parentCase; }

    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }

    public String getCourtName() { return courtName; }
    public void setCourtName(String courtName) { this.courtName = courtName; }

    public String getCourtCircuit() { return courtCircuit; }
    public void setCourtCircuit(String courtCircuit) { this.courtCircuit = courtCircuit; }

    public String getProcedureStage() { return procedureStage; }
    public void setProcedureStage(String procedureStage) { this.procedureStage = procedureStage; }

    public DomainEnums.DisputePathType getDisputePathType() { return disputePathType; }
    public void setDisputePathType(DomainEnums.DisputePathType disputePathType) { this.disputePathType = disputePathType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getCloseDate() { return closeDate; }
    public void setCloseDate(LocalDate closeDate) { this.closeDate = closeDate; }

    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }

    public Person getClient() { return client; }
    public void setClient(Person client) { this.client = client; }

    public Person getAssignedLawyer() { return assignedLawyer; }
    public void setAssignedLawyer(Person assignedLawyer) { this.assignedLawyer = assignedLawyer; }

    public Person getCreatedBy() { return createdBy; }
    public void setCreatedBy(Person createdBy) { this.createdBy = createdBy; }
}