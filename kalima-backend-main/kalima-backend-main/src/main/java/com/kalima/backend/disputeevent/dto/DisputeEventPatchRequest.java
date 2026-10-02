package com.kalima.backend.disputeevent.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Full replacement body for {@code PATCH} on a dispute event (same fields as create).
 */
public record DisputeEventPatchRequest(
        @NotNull(message = "type is required")
        DomainEnums.DisputeEventType type,

        @NotNull(message = "status is required")
        DomainEnums.DisputeEventStatus status,

        @NotBlank(message = "title is required")
        @Size(max = 250, message = "title must be at most 250 characters")
        String title,

        String summary,

        LocalDateTime eventDate,

        @NotNull(message = "sortOrder is required")
        Integer sortOrder
) {
}
