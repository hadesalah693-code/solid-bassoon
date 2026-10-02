package com.kalima.backend.assistant.dto;

import java.util.UUID;

/**
 * Response from {@code POST /api/lawyer-assistant/ask}.
 *
 * @param reply         Assistant's Arabic answer.
 * @param caseId        Echoes the case id from the request when context was
 *                      injected. {@code null} for general questions.
 * @param contextUsed   True when real case data was injected into the prompt.
 *                      Lets the UI badge the answer ("بناءً على بيانات القضية").
 * @param model         Echoes the model id that produced the reply.
 * @param provider      Provider that produced the reply, e.g. GEMINI or GROQ.
 */
public record AssistantAskResponse(
        String reply,
        UUID caseId,
        boolean contextUsed,
        boolean documentsUsed,
        String model,
        String provider
) {
}
