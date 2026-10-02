package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.VendorPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VendorPaymentResponse(
        UUID id,
        UUID billId,
        String billNumber,
        String vendorName,
        LocalDate paymentDate,
        BigDecimal amount,
        String method,
        UUID bankAccountId,
        String bankAccountName,
        String reference,
        String notes,
        UUID journalEntryId,
        BigDecimal billRemaining,
        boolean voided,
        Instant voidedAt) {

    public static VendorPaymentResponse from(VendorPayment p) {
        return new VendorPaymentResponse(
                p.getId(),
                p.getBill() == null ? null : p.getBill().getId(),
                p.getBill() == null ? null : p.getBill().getBillNumber(),
                p.getBill() == null || p.getBill().getVendor() == null
                        ? null : p.getBill().getVendor().getName(),
                p.getPaymentDate(),
                p.getAmount(),
                p.getMethod(),
                p.getBankAccount() == null ? null : p.getBankAccount().getId(),
                p.getBankAccount() == null ? null : p.getBankAccount().getNameAr(),
                p.getReference(),
                p.getNotes(),
                p.getJournalEntry() == null ? null : p.getJournalEntry().getId(),
                p.getBill() == null ? null : p.getBill().remaining(),
                p.getVoidedAt() != null,
                p.getVoidedAt());
    }
}