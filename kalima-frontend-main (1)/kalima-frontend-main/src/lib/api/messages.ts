import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type { MessageInboxItemDto, MessagePageResponseDto, MessageResponseDto } from "@/types/api";

export const messagesApi = {
  /**
   * Returns all accessible conversations with last-message preview and unread
   * count in a single request — replaces the N+1 pattern on the inbox page.
   */
  inbox: () => request<MessageInboxItemDto[]>("/messages/inbox"),

  list: (caseId: string, params?: { limit?: number; before?: string }) => {
    const search = new URLSearchParams();
    if (params?.limit != null) search.set("limit", String(params.limit));
    if (params?.before) search.set("before", params.before);
    const qs = search.toString();
    return request<MessagePageResponseDto>(
      `/cases/${caseId}/messages${qs ? `?${qs}` : ""}`,
    );
  },

  send: (caseId: string, content: string) =>
    request<MessageResponseDto>(`/cases/${caseId}/messages`, {
      method: "POST",
      body: { content },
    }),

  /** Fire-and-forget: notifies other participants that the current user is typing. */
  sendTyping: (caseId: string) =>
    request<void>(`/cases/${caseId}/typing`, { method: "POST" }),

  /** Send a message with optional attachment URL and/or reply reference. */
  sendEnhanced: (
    caseId: string,
    payload: { content?: string; attachmentUrl?: string; replyToMessageId?: string },
  ) =>
    request<MessageResponseDto>(`/cases/${caseId}/messages`, {
      method: "POST",
      body: payload,
    }),

  /**
   * Upload a file and return the server-side attachment URL.
   * Uses fetch directly because the request helper sends JSON bodies.
   */
  uploadAttachment: async (caseId: string, file: File): Promise<string> => {
    const fd = new FormData();
    fd.append("file", file);
    const res = await authenticatedFetch(
      `/messages/attachments?caseId=${encodeURIComponent(caseId)}`,
      { method: "POST", body: fd },
    );
    const json = (await res.json()) as { url: string };
    return json.url;
  },

  /** Toggle (add/remove) an emoji reaction on a message. */
  react: (caseId: string, messageId: string, emoji: string) =>
    request<{ reactions: Record<string, number>; myReaction: string | null }>(
      `/cases/${caseId}/messages/${messageId}/reactions`,
      { method: "POST", body: { emoji } },
    ),
};
