package com.kalima.backend.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One turn in a multi-turn conversation with the Lawyer Assistant. The
 * frontend keeps the running history and sends it back with every question
 * so the model has the full context.
 */
public record AssistantTurn(
        @NotNull
        Role role,
        @NotBlank
        @Size(max = 4000)
        String content
) {
    public enum Role {
        USER,
        ASSISTANT
    }
}
