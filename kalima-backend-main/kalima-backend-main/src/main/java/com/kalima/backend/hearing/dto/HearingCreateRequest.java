package com.kalima.backend.hearing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.kalima.backend.hearing.entity.HearingStatus;
import com.kalima.backend.hearing.entity.HearingType;

import java.time.LocalDateTime;

/**
 * Request body for creating a {@link com.kalima.backend.hearing.entity.Hearing}
 * under a case.
 */
public record HearingCreateRequest(
        @NotNull(message = "hearingDate is required")
        LocalDateTime hearingDate,

        @NotBlank(message = "courtName is required")
        @Size(max = 200, message = "courtName must be at most 200 characters")
        String courtName,

        @Size(max = 100, message = "chamber must be at most 100 characters")
        String chamber,

        String result,
        String notes,

        LocalDateTime nextHearingDate,

        HearingType hearingType,

        HearingStatus status,

        @Size(max = 50, message = "outcomeCode must be at most 50 characters")
        String outcomeCode,

        Boolean didAttend,

        @Size(max = 200, message = "presidingJudge must be at most 200 characters")
        String presidingJudge,

        java.util.UUID previousHearingId,

        @jakarta.validation.constraints.Positive
        Integer durationMinutes
) {
}
