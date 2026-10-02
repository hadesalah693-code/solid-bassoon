package com.kalima.backend.template.entity;

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

import java.util.UUID;

/**
 * A concrete document rendered from a {@link Template}.
 *
 * <p>{@link #renderedContent} is the snapshot of the template after the
 * placeholders were filled in, so the document remains stable even if the
 * source template is later edited.
 *
 * <p>Both {@link #client} and {@link #caseFile} are optional: a generated
 * document may be tied to neither (e.g. a generic quotation), to a client
 * only, or to a specific case.
 *
 * <p>Tenant-scoped via {@code company_id} + Hibernate {@code tenantFilter}.
 */
@Entity
@Table(name = "generated_documents")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class GeneratedDocument extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private Template template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Person client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private CaseFile caseFile;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DomainEnums.DocumentStatus status = DomainEnums.DocumentStatus.DRAFT;

    @Column(name = "rendered_content", nullable = false, columnDefinition = "text")
    private String renderedContent;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException(
                    "GeneratedDocument.company must be set before persist. "
                    + "The service layer is responsible for assigning the tenant from TenantContext.");
        }
        if (this.status == null) {
            this.status = DomainEnums.DocumentStatus.DRAFT;
        }
    }
}
