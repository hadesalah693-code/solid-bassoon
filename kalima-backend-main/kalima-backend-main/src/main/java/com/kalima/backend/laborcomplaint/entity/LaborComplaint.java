package com.kalima.backend.laborcomplaint.entity;

import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.domain.entity.BaseAuditEntity;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
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
@Table(name = "labor_complaints")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class LaborComplaint extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "complaint_number", nullable = false, length = 32)
    private String complaintNumber;

    @Column(name = "complaint_year", nullable = false)
    private int complaintYear;

    @Column(name = "registration_date", nullable = false)
    private LocalDate registrationDate;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "subject")
    private String subject;

    @Column(name = "summary")
    private String summary;

    @Column(name = "legal_request")
    private String legalRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DomainEnums.LaborComplaintStatus status;

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
    private DomainEnums.LaborComplaintPartyCapacity opponentCapacity;

    @Column(name = "saved_reason")
    private String savedReason;

    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    @Column(name = "referred_at")
    private LocalDateTime referredAt;

    @Column(name = "referral_notes")
    private String referralNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_case_id")
    private CaseFile linkedCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_dispute_id")
    private Dispute linkedDispute;

    @Column(name = "internal_reference", length = 250)
    private String internalReference;

    @Column(name = "external_partners")
    private String externalPartners;

    @Column(name = "opponent_representative", length = 250)
    private String opponentRepresentative;

    @Column(name = "cooperating_offices")
    private String cooperatingOffices;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private Person createdBy;

    @OneToMany(mappedBy = "laborComplaint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<LaborComplaintParty> parties = new ArrayList<>();

    @OneToMany(mappedBy = "laborComplaint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sessionDate ASC")
    private List<LaborComplaintMolSession> molSessions = new ArrayList<>();

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("LaborComplaint.company must be set before persist.");
        }
    }
}
