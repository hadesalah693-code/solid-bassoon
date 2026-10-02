package com.kalima.backend.delegation.entity;

import com.kalima.backend.domain.entity.BaseAuditEntity;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "delegations")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Delegation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delegation_number", nullable = false, length = 32)
    private String delegationNumber;

    /** Official notarization/document number printed on the physical POA (e.g. {@code 2026/123456}). */
    @Column(name = "notary_number", length = 50)
    private String notaryNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Person client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Person agent;

    /** Staff members acting as agents on this delegation (office attorneys / lawyers). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "delegation_agents",
            joinColumns = @JoinColumn(name = "delegation_id"))
    @Column(name = "person_id")
    private Set<UUID> agentIds = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "delegation_type", nullable = false, length = 30)
    private DomainEnums.DelegationType delegationType;

    @Column(name = "issuing_country", nullable = false, length = 2)
    private String issuingCountry = "QA";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DomainEnums.DelegationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private DomainEnums.DelegationCancelReason cancelReason;

    @Column(name = "cancel_reason_note", length = 500)
    private String cancelReasonNote;

    /** Date the cancellation document/instrument itself is dated. */
    @Column(name = "cancelled_at")
    private LocalDate cancelledAt;

    /** Date the firm was informed of the cancellation (may differ from {@link #cancelledAt}). */
    @Column(name = "cancellation_announcement_at")
    private LocalDate cancellationAnnouncementAt;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    /** Optional: some delegations never expire. */
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "starred", nullable = false)
    private boolean starred = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "delegation_reminder_offsets",
            joinColumns = @JoinColumn(name = "delegation_id"))
    @Column(name = "offset_days")
    private Set<Integer> reminderOffsets = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "delegation_reminder_recipients",
            joinColumns = @JoinColumn(name = "delegation_id"))
    @Column(name = "person_id")
    private Set<UUID> reminderRecipientIds = new HashSet<>();

    /** Optional co-principals/partners on the same delegation (persons with role CLIENT). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "delegation_partners",
            joinColumns = @JoinColumn(name = "delegation_id"))
    @Column(name = "person_id")
    private Set<UUID> partnerIds = new HashSet<>();

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("Delegation.company must be set before persist.");
        }
    }
}
