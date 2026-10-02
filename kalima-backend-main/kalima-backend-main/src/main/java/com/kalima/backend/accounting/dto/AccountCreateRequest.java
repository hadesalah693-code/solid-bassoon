package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Account.Type;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AccountCreateRequest(
        @NotBlank(message = "code is required")
        @Size(max = 20, message = "code must be at most 20 characters")
        String code,

        @NotBlank(message = "nameAr is required")
        @Size(max = 150, message = "nameAr must be at most 150 characters")
        String nameAr,

        @Size(max = 150, message = "nameEn must be at most 150 characters")
        String nameEn,

        @NotNull(message = "type is required")
        Type type,

        UUID parentId,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description) {
}
