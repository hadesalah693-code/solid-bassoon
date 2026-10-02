package com.kalima.backend.agent.voice.dto;

import java.util.List;
import java.util.UUID;

public record VoiceAgentResponse(
        String reply,
        boolean needsConfirmation,
        UUID pendingActionId,
        String pendingSummary,
        List<String> toolsUsed,
        UUID activeCaseId
) {
}
