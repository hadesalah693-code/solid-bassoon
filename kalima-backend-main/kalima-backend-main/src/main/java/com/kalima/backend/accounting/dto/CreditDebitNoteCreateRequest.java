package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.CreditDebitNote;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreditDebitNoteCreateRequest(
        @NotNull CreditDebitNote.NoteType noteType,
        UUID invoiceId,
        UUID clientId,
        LocalDate noteDate,
        @NotNull @DecimalMin(value = "0.01") BigDecimal subtotal,
        @DecimalMin("0") BigDecimal taxRate,
        @DecimalMin("0") BigDecimal taxAmount,
        @Size(max = 300) String reason,
        @Size(max = 1000) String notes) {
}