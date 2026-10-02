"use client";

import { useEffect, useState } from "react";

import { tasksApi } from "@/lib/api/tasks";

const STORAGE_KEY = "kalima.my_open_tasks";
const EVENT_NAME = "kalima:my_open_tasks_changed";

export function setMyOpenTasks(count: number): void {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(STORAGE_KEY, String(count));
  } catch {
    // Ignore storage errors.
  }
  window.dispatchEvent(new CustomEvent<number>(EVENT_NAME, { detail: count }));
}

export async function refreshMyOpenTasks(): Promise<number> {
  try {
    const { count } = await tasksApi.myOpenCount();
    setMyOpenTasks(count);
    return count;
  } catch {
    setMyOpenTasks(0);
    return 0;
  }
}

export function useMyOpenTasks(): number {
  const [count, setCount] = useState(0);

  useEffect(() => {
    const stored = parseInt(localStorage.getItem(STORAGE_KEY) ?? "0", 10);
    setCount(Number.isNaN(stored) ? 0 : stored);

    const handler = (e: Event) => {
      setCount((e as CustomEvent<number>).detail);
    };
    window.addEventListener(EVENT_NAME, handler);
    return () => window.removeEventListener(EVENT_NAME, handler);
  }, []);

  return count;
}
