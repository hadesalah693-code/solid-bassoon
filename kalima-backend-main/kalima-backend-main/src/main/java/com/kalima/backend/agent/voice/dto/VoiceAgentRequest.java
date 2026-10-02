package com.kalima.backend.agent.voice.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record VoiceAgentRequest(
        @NotBlank(message = "text is required")
        String text,

        /** When set with {@code confirm=true}, executes a pending destructive action. */
        UUID pendingActionId,

        Boolean confirm,

        /** Optional page context from the frontend overlay. */
        UUID caseId,

        UUID delegationId,

        UUID disputeId
) {
}
