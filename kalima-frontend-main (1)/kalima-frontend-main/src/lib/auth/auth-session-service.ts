import { authApi } from "@/lib/api/auth";
import { setAuthSnapshot } from "@/lib/auth/auth-store";
import { applySyncSessionRestore } from "@/lib/auth/session-sync";
import {
  clearSession,
  readSessionSnapshot,
  setSessionFromLogin,
} from "@/lib/auth/session";

let refreshInFlight: Promise<boolean> | null = null;

function syncStoreFromSession(): void {
  const { user, accessToken, isAuthenticated } = readSessionSnapshot();
  setAuthSnapshot({ user, accessToken, isAuthenticated });
}

/** Single-flight refresh — safe for parallel 401s and bootstrap. */
export async function refreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    try {
      const data = await authApi.refresh();
      setSessionFromLogin(data);
      setAuthSnapshot({
        user: data.person,
        accessToken: data.accessToken,
        isAuthenticated: true,
      });
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

export async function logoutSession(redirect = true): Promise<void> {
  try {
    await authApi.logout();
  } catch {
    /* network failure — still clear locally */
  }
  clearSession();
  setAuthSnapshot({
    user: null,
    accessToken: null,
    isAuthenticated: false,
  });
  if (redirect && typeof window !== "undefined") {
    window.location.assign("/login");
  }
}

/**
 * Startup / post-reload session restore.
 * Valid token → authenticated; partial local state → refresh via cookie.
 */
export async function bootstrapSession(): Promise<void> {
  const sync = applySyncSessionRestore();
  if (sync !== "needs_refresh") return;

  const refreshed = await refreshSession();
  if (refreshed) return;

  clearSession();
  setAuthSnapshot({
    user: null,
    accessToken: null,
    isAuthenticated: false,
  });
}

/** Called after login from UI — updates store without extra round-trip. */
export function applyLoginSession(data: Parameters<typeof setSessionFromLogin>[0]): void {
  setSessionFromLogin(data);
  setAuthSnapshot({
    user: data.person,
    accessToken: data.accessToken,
    isAuthenticated: true,
  });
}

/** Re-read localStorage into store (cross-tab sync). */
export function syncSessionFromStorage(): void {
  syncStoreFromSession();
}
