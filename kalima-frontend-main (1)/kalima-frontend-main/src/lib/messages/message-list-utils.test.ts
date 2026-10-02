import assert from "node:assert/strict";
import test from "node:test";

import { prependUniqueMessages } from "./message-list-utils";
import type { MessageResponseDto } from "@/types/api";

function msg(id: string, createdAt: string): MessageResponseDto {
  return {
    id,
    caseId: "case-1",
    sender: null,
    receiver: null,
    senderType: "USER",
    content: id,
    attachmentUrl: null,
    replyTo: null,
    read: true,
    readAt: null,
    createdAt,
    reactions: {},
    myReaction: null,
  };
}

test("prependUniqueMessages prepends older items in order", () => {
  const existing = [msg("b", "2026-01-02T10:00:00"), msg("c", "2026-01-02T11:00:00")];
  const older = [msg("a", "2026-01-02T09:00:00")];
  const merged = prependUniqueMessages(existing, older);
  assert.deepEqual(merged.map((m) => m.id), ["a", "b", "c"]);
});

test("prependUniqueMessages skips duplicate ids", () => {
  const existing = [msg("a", "2026-01-02T09:00:00"), msg("b", "2026-01-02T10:00:00")];
  const older = [msg("a", "2026-01-02T09:00:00"), msg("c", "2026-01-02T08:00:00")];
  const merged = prependUniqueMessages(existing, older);
  assert.deepEqual(merged.map((m) => m.id), ["c", "a", "b"]);
});

test("prependUniqueMessages returns existing when older is empty", () => {
  const existing = [msg("a", "2026-01-02T09:00:00")];
  assert.equal(prependUniqueMessages(existing, []), existing);
});
