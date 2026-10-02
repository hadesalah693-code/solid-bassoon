package com.kalima.backend.hearing.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.hearing.entity.HearingStatus;
import com.kalima.backend.hearing.entity.HearingType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Hearing row in the tenant-level calendar list (e.g. all hearings on a
 * day), with enough case context for dashboards without a second case fetch.
 */
public record HearingOfficeItemResponse(
        UUID id,
        UUID caseId,
        String caseNumber,
        String caseTitle,
        LocalDateTime hearingDate,
        String courtName,
        String chamber,
        String result,
        String notes,
        HearingType hearingType,
        HearingStatus status,
        String outcomeCode,
        Boolean didAttend,
        String presidingJudge,
        UUID previousHearingId,
        Integer durationMinutes,
        PartySummary client,
        PartySummary assignedLawyer,
        PartySummary createdBy,
        LocalDateTime createdAt
) {
}
