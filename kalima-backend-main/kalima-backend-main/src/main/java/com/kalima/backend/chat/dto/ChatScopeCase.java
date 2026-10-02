package com.kalima.backend.chat.dto;

import com.kalima.backend.domain.entity.DomainEnums;

import java.util.UUID;

/**
 * Minimal case info shown in the global chat picker. Returned only for
 * cases the authenticated actor is permitted to chat in — see
 * {@link com.kalima.backend.chat.service.ChatScopeService}.
 */
public record ChatScopeCase(
        UUID id,
        String caseNumber,
        String title,
        DomainEnums.CaseStatus status,
        boolean archived
) {
}
