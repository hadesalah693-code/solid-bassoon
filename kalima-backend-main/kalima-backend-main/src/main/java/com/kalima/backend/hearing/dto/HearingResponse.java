package com.kalima.backend.hearing.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.hearing.entity.HearingStatus;
import com.kalima.backend.hearing.entity.HearingType;

import java.time.LocalDateTime;
import java.util.UUID;

public record HearingResponse(
        UUID id,
        UUID caseId,
        LocalDateTime hearingDate,
        String courtName,
        String chamber,
        String result,
        String notes,
        LocalDateTime nextHearingDate,
        HearingType hearingType,
        HearingStatus status,
        String outcomeCode,
        Boolean didAttend,
        String presidingJudge,
        UUID previousHearingId,
        Integer durationMinutes,
        PartySummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
