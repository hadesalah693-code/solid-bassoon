"use client";

import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";

import { refreshSession } from "@/lib/auth/auth-session-service";
import { getAccessToken, isTokenExpired } from "@/lib/auth/session";
import { API_BASE_URL } from "@/lib/constants";
import type { MessageResponseDto } from "@/types/api";

export type TypingEventDto = {
  personId: string;
  personName: string;
};

/**
 * Build the STOMP WebSocket URL from {@link API_BASE_URL}.
 *
 *   http://host:port/api  → ws://host:port/ws
 *   https://host/api      → wss://host/ws
 *
 * The backend exposes the raw WebSocket endpoint at `/ws` (see
 * `WebSocketConfig`). This helper strips the `/api` REST suffix and swaps the
 * scheme for `ws` / `wss`.
 */
export function resolveWebSocketUrl(): string {
  let httpBase = API_BASE_URL;
  if (httpBase.endsWith("/api")) {
    httpBase = httpBase.slice(0, -"/api".length);
  } else if (httpBase.endsWith("/api/")) {
    httpBase = httpBase.slice(0, -"/api/".length);
  }
  if (httpBase.startsWith("https://")) {
    return "wss://" + httpBase.slice("https://".length) + "/ws";
  }
  if (httpBase.startsWith("http://")) {
    return "ws://" + httpBase.slice("http://".length) + "/ws";
  }
  // Fallback: same-origin
  if (typeof window !== "undefined") {
    const proto = window.location.protocol === "https:" ? "wss" : "ws";
    return `${proto}://${window.location.host}/ws`;
  }
  return "ws://localhost:8080/ws";
}

export type StreamStatus = "connecting" | "connected" | "disconnected";

export type CaseMessageStreamHandlers = {
  onMessage: (msg: MessageResponseDto) => void;
  onStatusChange?: (status: StreamStatus) => void;
  /** Called when another user sends a typing notification for this case. */
  onTyping?: (event: TypingEventDto) => void;
};

/**
 * Open a STOMP connection to `/ws`, subscribe to the per-case topic, and
 * forward every incoming {@link MessageResponseDto} to the caller.
 *
 * Returns a `dispose()` that unsubscribes and closes the underlying socket.
 * Safe to call from a `useEffect` cleanup.
 */
export function openCaseMessageStream(
  caseId: string,
  handlers: CaseMessageStreamHandlers,
): () => void {
  if (typeof window === "undefined") {
    return () => {};
  }

  const msgTopic    = `/topic/cases/${caseId}/messages`;
  const typingTopic = `/topic/cases/${caseId}/typing`;
  let subscription: StompSubscription | null = null;
  let typingSubscription: StompSubscription | null = null;
  let disposed = false;
  let client: Client | null = null;

  handlers.onStatusChange?.("connecting");

  void (async () => {
    if (isTokenExpired()) {
      await refreshSession();
    }
    const token = getAccessToken();
    if (!token || disposed) {
      handlers.onStatusChange?.("disconnected");
      return;
    }

    client = new Client({
      brokerURL: resolveWebSocketUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    });

    client.beforeConnect = async () => {
      if (isTokenExpired()) {
        await refreshSession();
      }
      const nextToken = getAccessToken();
      if (nextToken) {
        client!.connectHeaders = { Authorization: `Bearer ${nextToken}` };
      }
    };

    client.onConnect = () => {
      if (disposed) return;
      handlers.onStatusChange?.("connected");

      subscription = client!.subscribe(msgTopic, (frame: IMessage) => {
        try {
          const payload = JSON.parse(frame.body) as MessageResponseDto;
          handlers.onMessage(payload);
        } catch {
          /* ignore malformed frames */
        }
      });

      if (handlers.onTyping) {
        typingSubscription = client!.subscribe(typingTopic, (frame: IMessage) => {
          try {
            const event = JSON.parse(frame.body) as TypingEventDto;
            handlers.onTyping!(event);
          } catch {
            /* ignore malformed frames */
          }
        });
      }
    };

    client.onWebSocketClose = () => {
      if (!disposed) handlers.onStatusChange?.("disconnected");
    };
    client.onStompError = () => {
      if (!disposed) handlers.onStatusChange?.("disconnected");
    };

    client.activate();
  })();

  return () => {
    disposed = true;
    try { subscription?.unsubscribe(); } catch { /* ignore */ }
    try { typingSubscription?.unsubscribe(); } catch { /* ignore */ }
    void client?.deactivate();
    handlers.onStatusChange?.("disconnected");
  };
}
