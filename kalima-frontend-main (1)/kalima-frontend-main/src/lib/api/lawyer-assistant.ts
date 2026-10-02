import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";

/** One turn in the assistant conversation. Server is stateless — caller keeps history. */
export type AssistantTurn = {
  role: "USER" | "ASSISTANT";
  content: string;
};

export type AssistantAskRequest = {
  caseId?: string;
  delegationId?: string;
  disputeId?: string;
  history: AssistantTurn[];
  question: string;
};

export type AssistantAskResponse = {
  reply: string;
  caseId: string | null;
  contextUsed: boolean;
  documentsUsed: boolean;
  model: string;
  provider: "GEMINI" | "GROQ" | string;
};

export type AssistantStreamMeta = {
  caseId: string | null;
  contextUsed: boolean;
  documentsUsed: boolean;
  provider: string;
  model: string;
};

export type AssistantStreamHandlers = {
  onMeta?: (meta: AssistantStreamMeta) => void;
  onDelta?: (text: string) => void;
  onDone?: (meta: AssistantStreamMeta) => void;
  onError?: (message: string) => void;
};

async function askBlocking(body: AssistantAskRequest) {
  return request<AssistantAskResponse>("/lawyer-assistant/ask", {
    method: "POST",
    body,
  });
}

export const lawyerAssistantApi = {
  ask: askBlocking,

  /** POST /api/lawyer-assistant/ask/stream — SSE; falls back to /ask on failure. */
  askStream: async (
    body: AssistantAskRequest,
    handlers: AssistantStreamHandlers = {},
  ): Promise<AssistantAskResponse> => {
    let meta: AssistantStreamMeta | null = null;
    let doneMeta: AssistantStreamMeta | null = null;
    let fullText = "";

    try {
      const res = await authenticatedFetch("/lawyer-assistant/ask/stream", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "text/event-stream",
        },
        body: JSON.stringify(body),
      });

      if (!res.body) {
        throw new Error("Empty stream body");
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");

        let boundary = buffer.indexOf("\n\n");
        while (boundary >= 0) {
          const rawEvent = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          parseSseEvent(rawEvent, {
            onMeta: (payload) => {
              meta = payload;
              handlers.onMeta?.(payload);
            },
            onDelta: (text) => {
              fullText += text;
              handlers.onDelta?.(text);
            },
            onDone: (payload) => {
              doneMeta = payload;
              handlers.onDone?.(payload);
            },
            onError: (message) => {
              handlers.onError?.(message);
            },
          });
          boundary = buffer.indexOf("\n\n");
        }
      }

      const finalMeta = (doneMeta ?? meta) as AssistantStreamMeta | null;
      if (!finalMeta) {
        throw new Error("Stream ended without metadata");
      }

      return {
        reply: fullText,
        caseId: finalMeta.caseId,
        contextUsed: finalMeta.contextUsed,
        documentsUsed: finalMeta.documentsUsed,
        model: finalMeta.model,
        provider: finalMeta.provider,
      };
    } catch {
      const fallback = await askBlocking(body);
      if (!fullText.trim()) {
        handlers.onDelta?.(fallback.reply);
      }
      handlers.onDone?.({
        caseId: fallback.caseId,
        contextUsed: fallback.contextUsed,
        documentsUsed: fallback.documentsUsed,
        provider: fallback.provider,
        model: fallback.model,
      });
      return fallback;
    }
  },
};

function parseSseEvent(
  block: string,
  handlers: {
    onMeta: (meta: AssistantStreamMeta) => void;
    onDelta: (text: string) => void;
    onDone: (meta: AssistantStreamMeta) => void;
    onError: (message: string) => void;
  },
) {
  let eventName = "message";
  const dataLines: string[] = [];

  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trim());
    }
  }

  if (dataLines.length === 0) return;
  const dataRaw = dataLines.join("\n");

  try {
    if (eventName === "delta") {
      const parsed = JSON.parse(dataRaw) as { text?: string };
      if (parsed.text) handlers.onDelta(parsed.text);
      return;
    }
    if (eventName === "error") {
      const parsed = JSON.parse(dataRaw) as { message?: string };
      handlers.onError(parsed.message ?? "Stream error");
      return;
    }
    const parsed = JSON.parse(dataRaw) as AssistantStreamMeta;
    if (eventName === "meta") handlers.onMeta(parsed);
    if (eventName === "done") handlers.onDone(parsed);
  } catch {
    /* ignore malformed chunks */
  }
}
