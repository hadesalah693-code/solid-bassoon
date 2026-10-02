import type { CurrentUser } from "@/types/user";

export type AuthSnapshot = {
  user: CurrentUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
};

type Listener = (snapshot: AuthSnapshot) => void;

let snapshot: AuthSnapshot = {
  user: null,
  accessToken: null,
  isAuthenticated: false,
};

const listeners = new Set<Listener>();

export function getAuthSnapshot(): AuthSnapshot {
  return snapshot;
}

export function setAuthSnapshot(next: AuthSnapshot): void {
  snapshot = next;
  listeners.forEach((l) => l(snapshot));
}

export function subscribeAuth(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
