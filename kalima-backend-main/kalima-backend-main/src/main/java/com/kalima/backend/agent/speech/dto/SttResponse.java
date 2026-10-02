package com.kalima.backend.agent.speech.dto;

public record SttResponse(
        String text,
        String provider
) {
}
