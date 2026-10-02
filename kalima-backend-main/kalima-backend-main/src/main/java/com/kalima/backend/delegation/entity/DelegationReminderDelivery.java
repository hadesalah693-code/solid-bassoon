package com.kalima.backend.delegation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "delegation_reminder_deliveries")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
@IdClass(DelegationReminderDelivery.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class DelegationReminderDelivery {

    @Id
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Id
    @Column(name = "delegation_id", nullable = false)
    private UUID delegationId;

    @Id
    @Column(name = "offset_days", nullable = false)
    private Integer offsetDays;

    @Id
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;

    public DelegationReminderDelivery(
            UUID companyId, UUID delegationId, int offsetDays, UUID recipientId) {
        this.companyId = companyId;
        this.delegationId = delegationId;
        this.offsetDays = offsetDays;
        this.recipientId = recipientId;
    }

    @PrePersist
    void onCreate() {
        if (companyId == null) {
            throw new IllegalStateException("DelegationReminderDelivery.companyId is required");
        }
        if (notifiedAt == null) {
            notifiedAt = LocalDateTime.now();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID companyId;
        private UUID delegationId;
        private Integer offsetDays;
        private UUID recipientId;
    }
}
