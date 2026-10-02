package com.kalima.backend.delegation.settings.entity;

import com.kalima.backend.domain.entity.BaseAuditEntity;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A staff member pre-registered as an "office attorney" (وكيل مكتب) —
 * i.e. someone who commonly acts as the {@code agent} on the tenant's
 * delegations. Backs the Delegation Settings screen and lets delegation
 * creation auto-fill/suggest the agent instead of always requiring manual
 * selection.
 *
 * <p>Tenant-scoped like every other delegation-module entity: filtered via
 * the Hibernate {@code tenantFilter} and defended by the {@code company_id}
 * FK. At most one row per tenant may have {@link #isDefault} {@code true}
 * (enforced both here in the service layer and by a partial unique index).
 */
@Entity
@Table(name = "delegation_office_attorneys")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class DelegationOfficeAttorney extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    /** The staff member who may act as an agent; must have a role in {@code DelegationAgentRoles.AGENT_ROLES}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, updatable = false)
    private Person person;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("DelegationOfficeAttorney.company must be set before persist.");
        }
    }
}
