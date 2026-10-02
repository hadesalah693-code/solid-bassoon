import { setAuthSnapshot } from "@/lib/auth/auth-store";
import {
  clearSession,
  getAccessToken,
  getStoredUser,
  isTokenExpired,
} from "@/lib/auth/session";

/** Result of a synchronous local session read (no network). */
export type SyncSessionResult = "authenticated" | "anonymous" | "needs_refresh";

/**
 * Restores session from localStorage only.
 * {@code anonymous} — no credentials stored; skip refresh round-trip.
 * {@code needs_refresh} — expired/missing token but may have HttpOnly refresh cookie.
 */
export function applySyncSessionRestore(): SyncSessionResult {
  const token = getAccessToken();
  const user = getStoredUser();

  if (token && user && !isTokenExpired(0)) {
    setAuthSnapshot({
      user,
      accessToken: token,
      isAuthenticated: true,
    });
    return "authenticated";
  }

  if (!token && !user) {
    clearSession();
    setAuthSnapshot({
      user: null,
      accessToken: null,
      isAuthenticated: false,
    });
    return "anonymous";
  }

  return "needs_refresh";
}
