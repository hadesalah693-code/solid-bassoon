package com.kalima.backend.agent.speech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TtsRequest(
        @NotBlank @Size(max = 4000) String text,
        @Size(max = 64) String voice
) {
}
