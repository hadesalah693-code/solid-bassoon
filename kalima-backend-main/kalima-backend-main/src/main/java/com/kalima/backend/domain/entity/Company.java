package com.kalima.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Root tenant entity. Each row represents a law firm / subscribed customer.
 * Has no company_id of its own — it <em>is</em> the tenant.
 *
 * <p>Deliberately does NOT extend {@link BaseAuditEntity} to keep it decoupled
 * from tenant-scoped auditing logic. Timestamps are managed here directly.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    /** Fixed UUID of the default company seeded in migration V5. */
    public static final UUID DEFAULT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 250)
    private String legalName;

    @Column(name = "commercial_register", length = 100)
    private String commercialRegister;

    @Column(name = "tax_number", length = 100)
    private String taxNumber;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "website", length = 200)
    private String website;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "SA";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
