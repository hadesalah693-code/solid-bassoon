package com.kalima.backend.chat.dto;

import java.util.UUID;

/**
 * Minimal client info shown in the global chat picker. Returned only for
 * clients the authenticated actor is permitted to chat with — see
 * {@link com.kalima.backend.chat.service.ChatScopeService}.
 */
public record ChatScopeClient(
        UUID id,
        String fullName,
        String companyName,
        String email
) {
}
