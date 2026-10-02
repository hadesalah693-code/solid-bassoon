package com.kalima.backend.agent.speech.dto;

public record TtsResponse(
        String audioBase64,
        String contentType,
        String provider,
        String voice
) {
}
