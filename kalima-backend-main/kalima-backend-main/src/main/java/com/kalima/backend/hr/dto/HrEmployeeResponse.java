package com.kalima.backend.hr.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record HrEmployeeResponse(
        UUID id,
        String employeeNumber,
        String fullName,
        UUID departmentId,
        String departmentName,
        UUID positionId,
        String positionTitle,
        PartySummary person,
        String deviceUserId,
        String deviceUserPin,
        String cardNumber,
        String nationalId,
        DomainEnums.HrGender gender,
        String phone,
        String email,
        String address,
        LocalDate hireDate,
        DomainEnums.HrContractType contractType,
        LocalDate contractEndDate,
        BigDecimal basicSalary,
        DomainEnums.HrEmployeeStatus status,
        String notes,
        LocalDateTime terminatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
