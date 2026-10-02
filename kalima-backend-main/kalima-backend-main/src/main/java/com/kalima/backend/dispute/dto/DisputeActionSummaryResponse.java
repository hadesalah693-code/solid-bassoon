package com.kalima.backend.dispute.dto;

public record DisputeActionSummaryResponse(
        long openCount,
        long overdueCount,
        long doneCount,
        long cancelledCount
) {
}
