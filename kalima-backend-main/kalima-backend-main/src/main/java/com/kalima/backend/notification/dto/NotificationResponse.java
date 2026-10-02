package com.kalima.backend.notification.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        DomainEnums.NotificationType type,
        String title,
        String body,
        boolean read,
        UUID caseId,
        LocalDateTime createdAt
) {
}
