package com.kalima.backend.hr.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record HrAttendanceDeviceRequest(
        @NotBlank(message = "اسم الجهاز مطلوب")
        @Size(max = 150, message = "اسم الجهاز يجب أن يكون 150 حرفاً على الأكثر")
        String name,
        @Size(max = 250, message = "الموقع يجب أن يكون 250 حرفاً على الأكثر")
        String location,
        @NotBlank(message = "عنوان IP مطلوب")
        @Size(max = 64, message = "عنوان IP يجب أن يكون 64 حرفاً على الأكثر")
        String ipAddress,
        @Min(value = 1, message = "البورت يجب أن يكون بين 1 و 65535")
        @Max(value = 65535, message = "البورت يجب أن يكون بين 1 و 65535")
        Integer port,
        DomainEnums.HrDeviceProtocol protocol,
        @Size(max = 64, message = "السيريال نمبر يجب أن يكون 64 حرفاً على الأكثر")
        String serialNumber,
        DomainEnums.HrDeviceStatus status
) {
}
