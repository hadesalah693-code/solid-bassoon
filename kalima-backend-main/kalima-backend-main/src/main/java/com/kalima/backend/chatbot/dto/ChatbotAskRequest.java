package com.kalima.backend.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Question asked to the case chatbot. Arabic free-text; the service detects
 * intent based on keywords.
 */
public record ChatbotAskRequest(
        @NotBlank
        @Size(max = 500)
        String question
) {
}
