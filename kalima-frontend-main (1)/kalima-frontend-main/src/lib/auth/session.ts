import { STORAGE_KEYS, TOKEN_REFRESH_SKEW_MS } from "@/lib/constants";
import type { LoginResponse } from "@/lib/api/auth-types";
import type { CurrentUser } from "@/types/user";
import { isJwtExpired, decodeJwtExp, decodeJwtPayload } from "@/lib/auth/token-utils";

/**
 * Session from `POST /api/auth/login` or `/api/auth/refresh`.
 * Access JWT in localStorage; refresh token lives in HttpOnly cookie only.
 */
export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(STORAGE_KEYS.token);
}

/** @deprecated Use {@link getAccessToken}. Kept for existing call sites. */
export function getToken(): string | null {
  return getAccessToken();
}

export function getExpiresAt(): Date | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(STORAGE_KEYS.expiresAt);
  if (!raw) {
    const token = getAccessToken();
    if (!token) return null;
    const exp = decodeJwtExp(token);
    return exp != null ? new Date(exp * 1000) : null;
  }
  const d = new Date(raw);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function getStoredUser(): CurrentUser | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(STORAGE_KEYS.user);
  const parsed = parseStoredUser(raw);
  if (!parsed) return null;
  // Self-heal stale sessions: if the stored user was written by an older app
  // version without a role, derive it from the JWT so role-based navigation
  // (e.g. the accounting module) stays visible until the next login.
  if (!parsed.systemRole) {
    const token = getAccessToken();
    const payload = token ? decodeJwtPayload(token) : null;
    const role = payload?.role;
    if (typeof role === "string") {
      return { ...parsed, systemRole: role as CurrentUser["systemRole"] };
    }
  }
  return parsed;
}

function parseStoredUser(raw: string | null): CurrentUser | null {
  if (!raw) return null;
  try {
    const u = JSON.parse(raw) as CurrentUser;
    return typeof u === "object" && u !== null && typeof u.id === "string" ? u : null;
  } catch {
    return null;
  }
}

export function isTokenExpired(skewMs = TOKEN_REFRESH_SKEW_MS): boolean {
  const token = getAccessToken();
  if (!token) return true;
  const expiresAt = getExpiresAt();
  if (expiresAt) {
    return Date.now() >= expiresAt.getTime() - skewMs;
  }
  return isJwtExpired(token, skewMs);
}

export function setSessionFromLogin(data: LoginResponse): void {
  localStorage.setItem(STORAGE_KEYS.token, data.accessToken);
  localStorage.setItem(STORAGE_KEYS.expiresAt, data.expiresAt);
  const user: CurrentUser = {
    id: data.person.id,
    fullName: data.person.fullName ?? "",
    email: data.person.email,
    systemRole: data.person.systemRole,
  };
  localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(user));
}

export function clearSession(): void {
  localStorage.removeItem(STORAGE_KEYS.token);
  localStorage.removeItem(STORAGE_KEYS.user);
  localStorage.removeItem(STORAGE_KEYS.expiresAt);
}

export function readSessionSnapshot(): {
  user: CurrentUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
} {
  const accessToken = getAccessToken();
  const user = getStoredUser();
  const isAuthenticated = Boolean(
    accessToken && user && !isTokenExpired(0),
  );
  return { user: isAuthenticated ? user : null, accessToken, isAuthenticated };
}
