package com.kalima.backend.dispute.entity;

import com.kalima.backend.casefile.entity.CaseFile;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Dispute extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dispute_number", nullable = false, length = 32)
    private String disputeNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Person client;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_role", length = 30)
    private DomainEnums.DisputePartyCapacity clientRole;

    @Column(name = "opponent_name", nullable = false, length = 250)
    private String opponentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "opponent_role", length = 30)
    private DomainEnums.DisputePartyCapacity opponentRole;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_type", nullable = false, length = 30)
    private DomainEnums.DisputeType disputeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DomainEnums.DisputeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_path", length = 50)
    private DomainEnums.LegalPath legalPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 30)
    private DomainEnums.DisputeWorkflowStep currentStep = DomainEnums.DisputeWorkflowStep.CREATED;

    @Column(name = "claim_amount", precision = 19, scale = 2)
    private BigDecimal claimAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private DomainEnums.RiskLevel riskLevel;

    @Column(name = "success_probability")
    private Integer successProbability;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_lawyer_id")
    private Person assignedLawyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_case_id")
    private CaseFile convertedCase;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("Dispute.company must be set before persist.");
        }
        if (this.currentStep == null) {
            this.currentStep = DomainEnums.DisputeWorkflowStep.CREATED;
        }
    }
}
