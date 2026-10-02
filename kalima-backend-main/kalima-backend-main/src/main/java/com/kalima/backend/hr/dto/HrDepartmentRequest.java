package com.kalima.backend.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record HrDepartmentRequest(
        @NotBlank(message = "اسم القسم مطلوب")
        @Size(max = 150, message = "اسم القسم يجب أن يكون 150 حرفاً على الأكثر")
        String name,
        @Size(max = 40, message = "الكود يجب أن يكون 40 حرفاً على الأكثر")
        String code,
        UUID parentId,
        UUID managerId,
        @Size(max = 10000, message = "الوصف يجب أن يكون 10000 حرف على الأكثر")
        String description,
        Boolean active
) {
}
