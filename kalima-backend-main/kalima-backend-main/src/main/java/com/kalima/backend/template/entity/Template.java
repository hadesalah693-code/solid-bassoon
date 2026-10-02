package com.kalima.backend.template.entity;

import com.kalima.backend.domain.entity.BaseAuditEntity;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
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

/**
 * Reusable fillable form definition (quotation, invoice, receipt, contract …).
 *
 * <p>Tenant-scoped: every row carries {@code company_id} and is filtered by
 * the existing Hibernate {@code tenantFilter}. The DB-level FK to
 * {@code companies(id)} provides defence-in-depth against cross-tenant
 * persistence.
 *
 * <p>{@link #content} stores the template body (currently free-text or a
 * placeholder-based template; the rendering layer can interpret it). When a
 * tenant has multiple templates of the same {@link DomainEnums.TemplateType},
 * {@link #isDefault} marks the one chosen by default in the UI.
 */
@Entity
@Table(name = "templates")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Template extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private DomainEnums.TemplateType type;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException(
                    "Template.company must be set before persist. "
                    + "The service layer is responsible for assigning the tenant from TenantContext.");
        }
    }
}
