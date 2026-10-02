/** Decode JWT `exp` claim (seconds since epoch). Returns null if invalid. */
export function decodeJwtExp(token: string): number | null {
  const exp = decodeJwtPayload(token)?.exp;
  return typeof exp === "number" ? exp : null;
}

/** Decode the JWT payload (base64url, non-verifying). Returns null if invalid. */
export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length < 2) return null;
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = payload + "=".repeat((4 - (payload.length % 4)) % 4);
    return JSON.parse(atob(padded)) as Record<string, unknown>;
  } catch {
    return null;
  }
}

/** True when the token is expired or within `skewMs` of expiry. */
export function isJwtExpired(token: string, skewMs = 0): boolean {
  const exp = decodeJwtExp(token);
  if (exp == null) return true;
  return Date.now() >= exp * 1000 - skewMs;
}
