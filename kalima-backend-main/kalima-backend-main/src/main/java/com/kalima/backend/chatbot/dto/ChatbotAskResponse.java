package com.kalima.backend.chatbot.dto;

/**
 * Reply from the case chatbot.
 *
 * @param answer         Arabic natural-language answer.
 * @param matchedIntent  Internal intent label, or {@code UNKNOWN} when no
 *                       Arabic keyword matched (fallback path).
 */
public record ChatbotAskResponse(
        String answer,
        String matchedIntent
) {
}
