package com.kalima.backend.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated dashboard figures for the current tenant. All counts respect the
 * Hibernate tenant filter and business rules aligned with the case listing
 * APIs (non-archived cases; "active" cases exclude {@code CLOSED}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardSummaryResponse(
        long activeCaseCount,
        long hearingsTodayCount,
        long pendingTaskCount,
        /** Not supported until an invoices module exists — always {@code null}. */
        Long overdueInvoiceCount
) {
}
