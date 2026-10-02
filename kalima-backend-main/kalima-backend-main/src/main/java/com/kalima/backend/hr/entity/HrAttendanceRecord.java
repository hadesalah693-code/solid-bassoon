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
 * A single punch pulled from a device.
 *
 * <p>{@link #deviceUserId} is always populated (that is the only key the device
 * gives us), while {@link #employee} stays {@code null} until an employee with a
 * matching punch ID exists. Unmatched punches are kept rather than dropped so a
 * later import of the correct punch ID back-fills them automatically.
 */
@Entity
@Table(name = "hr_attendance_records")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class HrAttendanceRecord extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private HrAttendanceDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private HrEmployee employee;

    @Column(name = "device_user_id", nullable = false, length = 64)
    private String deviceUserId;

    @Column(name = "punched_at", nullable = false)
    private LocalDateTime punchedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false, length = 20)
    private DomainEnums.HrPunchType punchType = DomainEnums.HrPunchType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "verify_mode", nullable = false, length = 20)
    private DomainEnums.HrVerifyMode verifyMode = DomainEnums.HrVerifyMode.UNKNOWN;

    @Column(name = "work_code", length = 40)
    private String workCode;

    /** Raw device payload, kept for troubleshooting unmatched punches. */
    @Column(name = "raw_payload")
    private String rawPayload;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("HrAttendanceRecord.company must be set before persist.");
        }
    }
}
