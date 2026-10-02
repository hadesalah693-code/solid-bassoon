package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.TimeEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimeEntryResponse(
        UUID id,
        String entryNumber,
        UUID caseId,
        String caseNumber,
        String caseTitle,
        UUID lawyerId,
        String lawyerName,
        LocalDate workDate,
        int durationMinutes,
        String description,
        boolean billable,
        BigDecimal hourlyRate,
        BigDecimal amount,
        UUID invoiceId,
        UUID journalEntryId,
        boolean voided,
        Instant voidedAt) {

    public static TimeEntryResponse from(TimeEntry t) {
        return new TimeEntryResponse(
                t.getId(),
                t.getEntryNumber(),
                t.getCaseFile() == null ? null : t.getCaseFile().getId(),
                t.getCaseFile() == null ? null : t.getCaseFile().getCaseNumber(),
                t.getCaseFile() == null ? null : t.getCaseFile().getTitle(),
                t.getLawyer() == null ? null : t.getLawyer().getId(),
                t.getLawyer() == null ? null : t.getLawyer().getFullName(),
                t.getWorkDate(),
                t.getDurationMinutes(),
                t.getDescription(),
                t.isBillable(),
                t.getHourlyRate(),
                t.getAmount(),
                t.getInvoice() == null ? null : t.getInvoice().getId(),
                t.getJournalEntry() == null ? null : t.getJournalEntry().getId(),
                t.getVoidedAt() != null,
                t.getVoidedAt());
    }
}