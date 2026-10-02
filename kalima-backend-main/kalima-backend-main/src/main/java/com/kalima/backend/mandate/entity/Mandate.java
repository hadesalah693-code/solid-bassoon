package com.kalima.backend.mandate.entity;

import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.domain.entity.BaseAuditEntity;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.hearing.entity.Hearing;
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
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * إنابة محامٍ في جلسة/قضية (lawyer substitution).
 *
 * <p>Tracks the original lawyer, the substituted lawyer, the case, an optional
 * hearing, session/result details, and the agreed fees/payments.
 */
@Entity
@Table(name = "mandates")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
@SQLRestriction("deleted_at IS NULL")
public class Mandate extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "mandate_number", nullable = false, length = 32)
    private String mandateNumber;

    @Column(name = "mandate_year", nullable = false)
    private int mandateYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hearing_id")
    private Hearing hearing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_hearing_id")
    private Hearing nextHearing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Person client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_lawyer_id", nullable = false)
    private Person originalLawyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "substituted_lawyer_id", nullable = false)
    private Person substitutedLawyer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DomainEnums.MandateStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private DomainEnums.MandatePriority priority;

    @Column(name = "session_date")
    private LocalDateTime sessionDate;

    @Column(name = "court_name", length = 200)
    private String courtName;

    @Column(name = "court_circuit", length = 100)
    private String courtCircuit;

    @Column(name = "courtroom", length = 100)
    private String courtroom;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "fees", nullable = false, precision = 19, scale = 2)
    private BigDecimal fees = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "SAR";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private DomainEnums.MandatePaymentStatus paymentStatus =
            DomainEnums.MandatePaymentStatus.UNPAID;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    @Column(name = "session_result", columnDefinition = "TEXT")
    private String sessionResult;

    @Column(name = "court_decision", columnDefinition = "TEXT")
    private String courtDecision;

    @Column(name = "next_session_date")
    private LocalDateTime nextSessionDate;

    @Column(name = "next_session_notes", columnDefinition = "TEXT")
    private String nextSessionNotes;

    @Column(name = "did_attend")
    private Boolean didAttend;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private Person createdBy;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("Mandate.company must be set before persist.");
        }
    }
}
