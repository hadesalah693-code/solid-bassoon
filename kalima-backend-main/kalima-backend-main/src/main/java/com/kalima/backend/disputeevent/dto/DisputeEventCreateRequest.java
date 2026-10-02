package com.kalima.backend.disputeevent.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Request body for creating a {@link com.kalima.backend.disputeevent.entity.DisputeEvent}.
 */
public record DisputeEventCreateRequest(
        @NotNull(message = "type is required")
        DomainEnums.DisputeEventType type,

        @NotNull(message = "status is required")
        DomainEnums.DisputeEventStatus status,

        @NotBlank(message = "title is required")
        @Size(max = 250, message = "title must be at most 250 characters")
        String title,

        String summary,

        LocalDateTime eventDate,

        Integer sortOrder
) {
}
