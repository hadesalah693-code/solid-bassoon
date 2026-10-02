import { resolveApiBaseUrl } from "@/lib/resolve-api-base-url";

export const APP_NAME = "كلمة";
export const APP_NAME_EN = "Kalima";
export const APP_TAGLINE = "نظام إدارة مكاتب المحاماة";

/** Spring Boot API base, including `/api` (must match `server.servlet` context; default here is no extra context). */
export const API_BASE_URL = resolveApiBaseUrl();

export const DEFAULT_PAGE_SIZE = 20;

export const STORAGE_KEYS = {
  token: "kalima.token",
  user: "kalima.user",
  expiresAt: "kalima.token.expiresAt",
} as const;

/** Refresh access token this many ms before JWT expiry. */
export const TOKEN_REFRESH_SKEW_MS = 60_000;
