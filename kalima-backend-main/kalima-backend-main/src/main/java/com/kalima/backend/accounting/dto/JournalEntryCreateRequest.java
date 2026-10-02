package com.kalima.backend.accounting.dto;

import com.kalima.backend.accounting.entity.JournalEntry.Source;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalEntryCreateRequest(
        @NotNull(message = "entryDate is required")
        LocalDate entryDate,

        @NotNull(message = "description is required")
        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @NotNull(message = "lines must not be empty")
        @Size(min = 2, message = "a journal entry needs at least two lines")
        List<@NotNull LineRequest> lines) {

    /**
     * {@code @PositiveOrZero} rather than {@code @Positive}: a line legitimately
     * carries 0 on the side it is not using, and the UI sends exactly that. Requiring
     * a positive value on both columns made every manual journal entry fail with 400
     * before it ever reached the engine. The real invariants — no zero-value line, not
     * both sides at once, debits equal credits — are enforced in
     * {@code AccountingService#post} and by {@code chk_journal_lines_amount}.
     */
    public record LineRequest(
            @NotNull(message = "accountId is required")
            UUID accountId,

            @PositiveOrZero(message = "debit cannot be negative")
            BigDecimal debit,

            @PositiveOrZero(message = "credit cannot be negative")
            BigDecimal credit,

            @Size(max = 500, message = "description must be at most 500 characters")
            String description,

            UUID clientId,

            UUID bankAccountId) {
    }
}
