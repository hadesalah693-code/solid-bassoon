package com.kalima.backend.ocr.dto;

import java.util.List;

public record OcrStatsResponse(
        long totalDocuments,
        long processedToday,
        long processing,
        long failed,
        double successRatePercent,
        List<OcrActiveUserStat> mostActiveUsers
) {
}
