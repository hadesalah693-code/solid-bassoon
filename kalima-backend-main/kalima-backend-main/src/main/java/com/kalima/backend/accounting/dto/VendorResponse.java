package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Vendor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VendorResponse(
        UUID id,
        String name,
        String nameAr,
        String email,
        String phone,
        String taxNumber,
        String address,
        String currency,
        UUID expenseAccountId,
        boolean active,
        BigDecimal outstandingPayable,
        String notes,
        Instant createdAt) {

    public static VendorResponse from(Vendor v, BigDecimal outstanding) {
        BigDecimal o = outstanding == null ? BigDecimal.ZERO : outstanding;
        return new VendorResponse(
                v.getId(), v.getName(), v.getNameAr(), v.getEmail(), v.getPhone(),
                v.getTaxNumber(), v.getAddress(), v.getCurrency(),
                v.getExpenseAccount() == null ? null : v.getExpenseAccount().getId(),
                v.isActive(), o, v.getNotes(), v.getCreatedAt());
    }
}