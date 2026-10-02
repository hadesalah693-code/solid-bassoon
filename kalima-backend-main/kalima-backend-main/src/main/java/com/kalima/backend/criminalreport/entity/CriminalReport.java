package com.kalima.backend.criminalreport.entity;

import com.kalima.backend.domain.entity.BaseAuditEntity;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.dispute.entity.Dispute;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "criminal_reports")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class CriminalReport extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "report_number", nullable = false, length = 32)
    private String reportNumber;

    @Column(name = "report_year", nullable = false)
    private int reportYear;

    @Column(name = "registration_date", nullable = false)
    private LocalDate registrationDate;

    @Column(name = "incident_date")
    private LocalDate incidentDate;

    @Column(name = "prosecution_office", length = 200)
    private String prosecutionOffice;

    @Column(name = "security_center", length = 200)
    private String securityCenter;

    @Column(name = "charge", length = 500)
    private String charge;

    @Column(name = "subject")
    private String subject;

    @Column(name = "summary")
    private String summary;

    @Column(name = "legal_request")
    private String legalRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DomainEnums.CriminalReportStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Person client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_lawyer_id")
    private Person assignedLawyer;

    @Column(name = "opponent_name", length = 250)
    private String opponentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "opponent_capacity", length = 60)
    private DomainEnums.CriminalReportPartyCapacity opponentCapacity;

    @Column(name = "archive_reason")
    private String archiveReason;

    @Column(name = "grievance_period_days")
    private Integer grievancePeriodDays;

    @Column(name = "grievance_deadline")
    private LocalDate grievanceDeadline;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "final_archive_reason")
    private String finalArchiveReason;

    @Column(name = "decision_issuer", length = 250)
    private String decisionIssuer;

    @Column(name = "decision_number", length = 100)
    private String decisionNumber;

    @Column(name = "decision_received_date")
    private LocalDate decisionReceivedDate;

    @Column(name = "final_decision_basis")
    private String finalDecisionBasis;

    @Column(name = "appeal_period_days")
    private Integer appealPeriodDays;

    @Column(name = "final_decision_level", length = 100)
    private String finalDecisionLevel;

    @Column(name = "final_archived_at")
    private LocalDateTime finalArchivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_case_id")
    private CaseFile linkedCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_dispute_id")
    private Dispute linkedDispute;

    @Column(name = "internal_source", length = 250)
    private String internalSource;

    @Column(name = "legal_consultant", length = 250)
    private String legalConsultant;

    @Column(name = "external_consultant", length = 250)
    private String externalConsultant;

    @Column(name = "partner_notes")
    private String partnerNotes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private Person createdBy;

    @OneToMany(mappedBy = "criminalReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CriminalReportParty> parties = new ArrayList<>();

    @OneToMany(mappedBy = "criminalReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<CriminalReportLocation> locations = new ArrayList<>();

    @OneToMany(mappedBy = "criminalReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sessionDate ASC")
    private List<CriminalReportInvestigationSession> investigationSessions = new ArrayList<>();

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("CriminalReport.company must be set before persist.");
        }
    }
}
