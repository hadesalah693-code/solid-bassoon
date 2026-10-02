package com.kalima.backend.domain.entity;

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
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "persons")
@Getter
@Setter
@NoArgsConstructor
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "companyId", type = UUID.class)
)
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Person extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 20)
    private DomainEnums.PersonType personType;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 30)
    private DomainEnums.SystemRole systemRole;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "national_id", length = 50)
    private String nationalId;

    @Column(name = "commercial_register", length = 100)
    private String commercialRegister;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 150, unique = true)
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "notes")
    private String notes;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * When set, the person is considered archived: excluded from default listings
     * and may not sign in. Not a hard delete from the database.
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /**
     * For {@code CLIENT_USER} (portal login) rows only: points at the firm-side
     * {@code CLIENT} {@link Person} that this portal account is allowed to act
     * as. The portal user authenticates through the normal JWT pipeline but is
     * scoped to exactly one client profile (tenant-isolated, enforced by the
     * {@code uq_persons_portal_client_per_company} unique index and the
     * {@code @Filter} on the self-referencing join).
     */
    @Column(name = "portal_client_id")
    private UUID portalClientId;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException(
                    "Person.company must be set before persist. "
                    + "The service layer is responsible for assigning the tenant from TenantContext.");
        }
    }
}
