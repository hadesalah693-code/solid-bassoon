package com.kalima.backend.accounting.service;

import java.time.LocalDate;

/**
 * PostgreSQL cannot infer the type of a NULL bind parameter, which breaks
 * queries shaped like {@code (:from is null or col >= :from)}. Reporting queries
 * therefore always receive concrete dates and the {@code is null} branches simply
 * never fire.
 */
public final class AccountingDates {

    /** Effectively "beginning of time" for ledger queries. */
    public static final LocalDate MIN = LocalDate.of(1900, 1, 1);
    /** Effectively "end of time" for ledger queries. */
    public static final LocalDate MAX = LocalDate.of(2999, 12, 31);

    private AccountingDates() {
    }

    public static LocalDate from(LocalDate date) {
        return date == null ? MIN : date;
    }

    public static LocalDate to(LocalDate date) {
        return date == null ? MAX : date;
    }
}
