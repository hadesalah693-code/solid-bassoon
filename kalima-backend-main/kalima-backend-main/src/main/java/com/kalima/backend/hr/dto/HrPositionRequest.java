package com.kalima.backend.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record HrPositionRequest(
        @NotBlank(message = "المسمى الوظيفي مطلوب")
        @Size(max = 150, message = "المسمى الوظيفي يجب أن يكون 150 حرفاً على الأكثر")
        String title,
        @Size(max = 40, message = "الكود يجب أن يكون 40 حرفاً على الأكثر")
        String code,
        UUID departmentId,
        @Size(max = 10000, message = "الوصف يجب أن يكون 10000 حرف على الأكثر")
        String description,
        Boolean active
) {
}
