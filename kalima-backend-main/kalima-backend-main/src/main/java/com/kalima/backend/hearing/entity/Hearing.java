package com.kalima.backend.hearing.entity;

import com.kalima.backend.domain.entity.BaseAuditEntity;
import com.kalima.backend.casefile.entity.CaseFile;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hearings")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Hearing extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "hearing_date", nullable = false)
    private LocalDateTime hearingDate;

    @Column(name = "court_name", nullable = false, length = 200)
    private String courtName;

    @Column(name = "chamber", length = 100)
    private String chamber;

    @Column(name = "result")
    private String result;

    @Column(name = "notes")
    private String notes;

    @Column(name = "next_hearing_date")
    private LocalDateTime nextHearingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "hearing_type", nullable = false, length = 30)
    private HearingType hearingType = HearingType.ORDINARY;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HearingStatus status = HearingStatus.SCHEDULED;

    @Column(name = "outcome_code", length = 50)
    private String outcomeCode;

    @Column(name = "did_attend")
    private Boolean didAttend;

    @Column(name = "presiding_judge", length = 200)
    private String presidingJudge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_hearing_id")
    private Hearing previousHearing;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Person createdBy;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException(
                    "Hearing.company must be set before persist.");
        }
    }

    // --- explicit getters/setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public CaseFile getCaseFile() { return caseFile; }
    public void setCaseFile(CaseFile caseFile) { this.caseFile = caseFile; }

    public LocalDateTime getHearingDate() { return hearingDate; }
    public void setHearingDate(LocalDateTime hearingDate) { this.hearingDate = hearingDate; }

    public String getCourtName() { return courtName; }
    public void setCourtName(String courtName) { this.courtName = courtName; }

    public String getChamber() { return chamber; }
    public void setChamber(String chamber) { this.chamber = chamber; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getNextHearingDate() { return nextHearingDate; }
    public void setNextHearingDate(LocalDateTime nextHearingDate) { this.nextHearingDate = nextHearingDate; }

    public HearingType getHearingType() { return hearingType; }
    public void setHearingType(HearingType hearingType) { this.hearingType = hearingType; }

    public HearingStatus getStatus() { return status; }
    public void setStatus(HearingStatus status) { this.status = status; }

    public String getOutcomeCode() { return outcomeCode; }
    public void setOutcomeCode(String outcomeCode) { this.outcomeCode = outcomeCode; }

    public Boolean getDidAttend() { return didAttend; }
    public void setDidAttend(Boolean didAttend) { this.didAttend = didAttend; }

    public String getPresidingJudge() { return presidingJudge; }
    public void setPresidingJudge(String presidingJudge) { this.presidingJudge = presidingJudge; }

    public Hearing getPreviousHearing() { return previousHearing; }
    public void setPreviousHearing(Hearing previousHearing) { this.previousHearing = previousHearing; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public Person getCreatedBy() { return createdBy; }
    public void setCreatedBy(Person createdBy) { this.createdBy = createdBy; }
}