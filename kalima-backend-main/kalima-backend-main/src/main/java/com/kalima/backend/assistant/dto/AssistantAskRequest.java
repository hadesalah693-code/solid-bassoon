package com.kalima.backend.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for {@code POST /api/lawyer-assistant/ask}.
 *
 * @param caseId   Optional case to ground the answer in real data. When set,
 *                 the backend enforces case access rules and injects a
 *                 structured snapshot of the case into the prompt. When
 *                 {@code null}, the assistant answers in general legal terms.
 * @param history  Prior turns of the conversation (oldest first). Caller
 *                 keeps state; the server is stateless.
 * @param question Latest free-text question from the lawyer. Required.
 */
public record AssistantAskRequest(
        UUID caseId,

        UUID delegationId,

        UUID disputeId,

        @Valid
        @Size(max = 30, message = "history must contain at most 30 turns")
        List<AssistantTurn> history,

        @NotBlank
        @Size(max = 2000)
        String question
) {
}
