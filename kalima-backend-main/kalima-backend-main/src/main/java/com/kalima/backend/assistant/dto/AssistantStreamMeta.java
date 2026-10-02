package com.kalima.backend.assistant.dto;

import java.util.UUID;

/** Initial SSE event for {@code POST /api/lawyer-assistant/ask/stream}. */
public record AssistantStreamMeta(
        UUID caseId,
        boolean contextUsed,
        boolean documentsUsed,
        String provider,
        String model
) {
}
