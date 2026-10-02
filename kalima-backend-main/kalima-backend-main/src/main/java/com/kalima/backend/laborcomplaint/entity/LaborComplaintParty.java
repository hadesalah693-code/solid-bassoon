package com.kalima.backend.laborcomplaint.entity;

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

import java.util.UUID;

@Entity
@Table(name = "labor_complaint_parties")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class LaborComplaintParty extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "labor_complaint_id", nullable = false, updatable = false)
    private LaborComplaint laborComplaint;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_kind", nullable = false, length = 20)
    private DomainEnums.LaborComplaintPartyKind partyKind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(name = "display_name", length = 250)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity", length = 60)
    private DomainEnums.LaborComplaintPartyCapacity capacity;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("LaborComplaintParty.company must be set before persist.");
        }
    }
}
