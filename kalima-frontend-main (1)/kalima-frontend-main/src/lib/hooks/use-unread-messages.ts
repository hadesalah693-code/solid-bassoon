"use client";

import { useEffect, useState } from "react";

const STORAGE_KEY = "kalima.unread_messages";
const EVENT_NAME  = "kalima:unread_messages_changed";

/**
 * Persist + broadcast the total unread message count so any mounted component
 * (e.g. the sidebar badge) stays in sync without a shared React context.
 */
export function setUnreadMessages(count: number): void {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(STORAGE_KEY, String(count));
  } catch {
    // Ignore storage errors (private browsing, quota).
  }
  window.dispatchEvent(new CustomEvent<number>(EVENT_NAME, { detail: count }));
}

/**
 * Returns the current total unread message count and re-renders whenever
 * {@link setUnreadMessages} is called anywhere in the application.
 */
export function useUnreadMessages(): number {
  const [count, setCount] = useState(0);

  useEffect(() => {
    // Hydrate from storage on mount.
    const stored = parseInt(localStorage.getItem(STORAGE_KEY) ?? "0", 10);
    setCount(isNaN(stored) ? 0 : stored);

    const handler = (e: Event) => {
      setCount((e as CustomEvent<number>).detail);
    };
    window.addEventListener(EVENT_NAME, handler);
    return () => window.removeEventListener(EVENT_NAME, handler);
  }, []);

  return count;
}
