package com.kalima.backend.hr.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Manual punch entry. Used for devices that cannot be reached over the network
 * (exported CSV/Excel, offline terminal) and for correcting a wrong punch.
 */
public record HrManualPunchRequest(
        @NotNull(message = "الجهاز مطلوب")
        java.util.UUID deviceId,
        @NotBlank(message = "معرف البصمة مطلوب")
        @Size(max = 64, message = "معرف البصمة يجب أن يكون 64 حرفاً على الأكثر")
        String deviceUserId,
        @NotNull(message = "وقت البصمة مطلوب")
        LocalDateTime punchedAt,
        DomainEnums.HrPunchType punchType,
        DomainEnums.HrVerifyMode verifyMode,
        @Size(max = 40, message = "كود العمل يجب أن يكون 40 حرفاً على الأكثر")
        String workCode
) {
}
