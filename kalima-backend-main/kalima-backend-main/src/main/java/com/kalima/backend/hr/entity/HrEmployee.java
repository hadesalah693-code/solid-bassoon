package com.kalima.backend.hr.entity;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An employee record.
 *
 * <p>{@link #deviceUserId} is the punch ID stored <em>inside</em> the fingerprint
 * device. Attendance syncs are matched on this value only — never on the
 * employee's name — so it is unique per tenant (enforced by a partial unique
 * index in {@code V64__hr_module.sql}).
 */
@Entity
@Table(name = "hr_employees")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class HrEmployee extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "employee_number", nullable = false, length = 40)
    private String employeeNumber;

    @Column(name = "full_name", nullable = false, length = 250)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private HrDepartment department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private HrPosition position;

    /** System login this employee uses, when the employee is also a staff member. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(name = "device_user_id", length = 64)
    private String deviceUserId;

    @Column(name = "device_user_pin", length = 64)
    private String deviceUserPin;

    @Column(name = "card_number", length = 64)
    private String cardNumber;

    @Column(name = "national_id", length = 40)
    private String nationalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private DomainEnums.HrGender gender;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "address", length = 250)
    private String address;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", length = 30)
    private DomainEnums.HrContractType contractType;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    @Column(name = "basic_salary", precision = 14, scale = 2)
    private BigDecimal basicSalary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DomainEnums.HrEmployeeStatus status = DomainEnums.HrEmployeeStatus.ACTIVE;

    @Column(name = "notes")
    private String notes;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @PrePersist
    void ensureTenant() {
        if (this.company == null) {
            throw new IllegalStateException("HrEmployee.company must be set before persist.");
        }
    }
}
