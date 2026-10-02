package com.kalima.backend.hr.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Create / update payload for an employee. {@code deviceUserId} is the punch ID
 * stored inside the fingerprint device and must be unique per tenant — it is
 * what attendance syncs are matched on.
 */
public record HrEmployeeRequest(
        @NotBlank(message = "الرقم الوظيفي مطلوب")
        @Size(max = 40, message = "الرقم الوظيفي يجب أن يكون 40 حرفاً على الأكثر")
        String employeeNumber,
        @NotBlank(message = "اسم الموظف مطلوب")
        @Size(max = 250, message = "اسم الموظف يجب أن يكون 250 حرفاً على الأكثر")
        String fullName,
        UUID departmentId,
        UUID positionId,
        UUID personId,
        @Size(max = 64, message = "معرف البصمة يجب أن يكون 64 حرفاً على الأكثر")
        String deviceUserId,
        @Size(max = 64, message = "PIN الجهاز يجب أن يكون 64 حرفاً على الأكثر")
        String deviceUserPin,
        @Size(max = 64, message = "رقم الكارت يجب أن يكون 64 حرفاً على الأكثر")
        String cardNumber,
        @Size(max = 40, message = "الرقم القومي يجب أن يكون 40 حرفاً على الأكثر")
        String nationalId,
        DomainEnums.HrGender gender,
        @Size(max = 40, message = "رقم الهاتف يجب أن يكون 40 حرفاً على الأكثر")
        String phone,
        @Email(message = "البريد الإلكتروني غير صحيح")
        @Size(max = 150, message = "البريد الإلكتروني يجب أن يكون 150 حرفاً على الأكثر")
        String email,
        @Size(max = 250, message = "العنوان يجب أن يكون 250 حرفاً على الأكثر")
        String address,
        LocalDate hireDate,
        DomainEnums.HrContractType contractType,
        LocalDate contractEndDate,
        @DecimalMin(value = "0.00", message = "الراتب الأساسي لا يمكن أن يكون سالباً")
        BigDecimal basicSalary,
        DomainEnums.HrEmployeeStatus status,
        @Size(max = 10000, message = "الملاحظات يجب أن تكون 10000 حرف على الأكثر")
        String notes
) {
}
