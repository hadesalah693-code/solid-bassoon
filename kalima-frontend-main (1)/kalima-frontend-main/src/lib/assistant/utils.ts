import type { AssistantChatMessage, AssistantTurn } from "@/lib/assistant/types";

export const AUTO_SPEAK_KEY = "kalima-auto-speak";

export function generateMessageId(): string {
  if (
    typeof globalThis !== "undefined" &&
    "crypto" in globalThis &&
    typeof globalThis.crypto.randomUUID === "function"
  ) {
    return globalThis.crypto.randomUUID();
  }
  return `m_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

export function toServerHistory(messages: AssistantChatMessage[]): AssistantTurn[] {
  return messages
    .filter((m) => !m.streaming && m.content.trim())
    .map((m) => ({
      role: m.role === "user" ? "USER" : "ASSISTANT",
      content: m.content,
    }));
}

export function readAutoSpeakPreference(): boolean {
  if (typeof window === "undefined") return true;
  try {
    return localStorage.getItem(AUTO_SPEAK_KEY) !== "false";
  } catch {
    return true;
  }
}

export function writeAutoSpeakPreference(value: boolean): void {
  try {
    localStorage.setItem(AUTO_SPEAK_KEY, String(value));
  } catch {
    /* ignore */
  }
}

/** Reveal assistant text progressively for a Siri-like feel. */
export async function revealMessageText(
  fullText: string,
  onChunk: (partial: string) => void,
  options?: { chunkMs?: number; charsPerTick?: number },
): Promise<void> {
  const chunkMs = options?.chunkMs ?? 12;
  const charsPerTick = options?.charsPerTick ?? 3;
  let i = 0;
  while (i < fullText.length) {
    i = Math.min(fullText.length, i + charsPerTick);
    onChunk(fullText.slice(0, i));
    await new Promise((r) => window.setTimeout(r, chunkMs));
  }
}
