package com.kalima.backend.disputeevent.dto;

import com.kalima.backend.domain.entity.DomainEnums;

/**
 * One row in the default workflow template for a {@link DomainEnums.DisputePathType}.
 */
public record DisputeWorkflowTemplateStep(
        DomainEnums.DisputeEventType type,
        DomainEnums.DisputeEventStatus status,
        String title
) {
}
