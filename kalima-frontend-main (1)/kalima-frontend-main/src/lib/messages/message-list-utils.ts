import type { MessageResponseDto } from "@/types/api";

/** Prepend older page items without duplicating ids already in the thread. */
export function prependUniqueMessages(
  existing: MessageResponseDto[],
  older: MessageResponseDto[],
): MessageResponseDto[] {
  if (older.length === 0) {
    return existing;
  }
  const seen = new Set(existing.map((m) => m.id));
  const uniqueOlder = older.filter((m) => !seen.has(m.id));
  if (uniqueOlder.length === 0) {
    return existing;
  }
  return [...uniqueOlder, ...existing];
}
