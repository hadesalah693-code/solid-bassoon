package com.kalima.backend.agent.night.dto;

import java.util.List;
import java.util.UUID;

public record NightAgentReport(
        List<InsightItem> highRiskCases,
        List<InsightItem> warnings,
        List<InsightItem> suggestions,
        List<AutoActionRecord> autoActions
) {
    public record InsightItem(
            String code,
            String message,
            UUID caseId,
            String caseNumber
    ) {
    }

    public record AutoActionRecord(
            String action,
            String detail,
            UUID caseId
    ) {
    }
}
