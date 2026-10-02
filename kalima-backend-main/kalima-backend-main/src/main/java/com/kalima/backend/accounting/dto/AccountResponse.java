package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Account.Type;

import java.util.UUID;

public record AccountResponse(
        UUID id,
        String code,
        String nameAr,
        String nameEn,
        Type type,
        UUID parentId,
        String parentCode,
        boolean active,
        boolean system,
        String description) {
}
