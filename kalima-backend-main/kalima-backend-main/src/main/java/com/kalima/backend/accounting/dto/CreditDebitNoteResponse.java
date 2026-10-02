package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.CreditDebitNote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreditDebitNoteResponse(
        UUID id,
        String noteNumber,
        CreditDebitNote.NoteType noteType,
        LocalDate noteDate,
        UUID invoiceId,
        String invoiceNumber,
        UUID clientId,
        String clientName,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        String reason,
        String notes,
        UUID journalEntryId,
        boolean voided,
        Instant voidedAt) {

    public static CreditDebitNoteResponse from(CreditDebitNote n) {
        return new CreditDebitNoteResponse(
                n.getId(),
                n.getNoteNumber(),
                n.getNoteType(),
                n.getNoteDate(),
                n.getInvoice() == null ? null : n.getInvoice().getId(),
                n.getInvoice() == null ? null : n.getInvoice().getInvoiceNumber(),
                n.getClient() == null ? null : n.getClient().getId(),
                n.getClient() == null ? null : n.getClient().getFullName(),
                n.getSubtotal(),
                n.getTaxRate(),
                n.getTaxAmount(),
                n.getTotal(),
                n.getReason(),
                n.getNotes(),
                n.getJournalEntry() == null ? null : n.getJournalEntry().getId(),
                n.getVoidedAt() != null,
                n.getVoidedAt());
    }
}