package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.Account.Type;
import com.kalima.backend.accounting.entity.JournalEntry.Source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        String entryNumber,
        LocalDate entryDate,
        String description,
        String referenceType,
        UUID referenceId,
        Source source,
        boolean posted,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        String createdBy,
        List<LineResponse> lines) {

    public record LineResponse(
            UUID id,
            int lineNo,
            UUID accountId,
            String accountCode,
            String accountName,
            Type accountType,
            BigDecimal debit,
            BigDecimal credit,
            String description,
            UUID clientId,
            String clientName) {
    }
}
