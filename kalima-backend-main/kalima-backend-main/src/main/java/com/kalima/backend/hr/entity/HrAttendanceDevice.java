package com.kalima.backend.hr.entity;

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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A fingerprint / face terminal the office pulls punches from.
 *
 * <p>Credentials live here rather than in config because each office runs its
 * own devices on its own LAN, and the device list is edited from the UI.
 */
@Entity
@Table(name = "hr_attendance_devices")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class HrAttendanceDevice extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "location", length = 250)
    private String location;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    /** TCP port of the standalone SDK listener. 4370 is the ZKTeco default. */
    @Column(name = "port", nullable = false)
    private int port = 4370;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 30)
    private DomainEnums.HrDeviceProtocol protocol = DomainEnums.HrDeviceProtocol.ZK_TECO_TCP;

    /** Serial reported by iClock push devices; identifies the device without auth. */
    @Column(name = "serial_number", length = 64)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DomainEnums.HrDeviceStatus status = DomainEnums.HrDeviceStatus.ACTIVE;

    /** Timestamp of the newest punch pulled from this device. */
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    /** Human-readable outcome of the last sync, shown as a hint in the UI. */
    @Column(name = "last_sync_message", length = 500)
    private String lastSyncMessage;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("HrAttendanceDevice.company must be set before persist.");
        }
    }
}
