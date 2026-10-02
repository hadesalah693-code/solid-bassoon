package com.kalima.backend.hearing.dto;

import com.kalima.backend.hearing.entity.HearingStatus;
import jakarta.validation.constraints.NotNull;

public record HearingStatusUpdateRequest(
        @NotNull HearingStatus status,
        Boolean didAttend,
        String outcomeCode
) {
}