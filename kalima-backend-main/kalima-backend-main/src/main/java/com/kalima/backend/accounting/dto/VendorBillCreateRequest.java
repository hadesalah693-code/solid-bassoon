package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.VendorBill;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VendorBillCreateRequest(
        UUID vendorId,
        LocalDate billDate,
        LocalDate dueDate,
        @NotNull @DecimalMin(value = "0.01") BigDecimal subtotal,
        @DecimalMin("0") BigDecimal taxRate,
        @DecimalMin("0") BigDecimal taxAmount,
        @NotNull UUID expenseAccountId,
        @Size(max = 100) String reference,
        @Size(max = 1000) String notes) {
}