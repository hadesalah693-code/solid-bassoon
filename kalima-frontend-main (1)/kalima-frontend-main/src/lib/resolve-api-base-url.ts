/** Hosted Kalima API (Render). Override via NEXT_PUBLIC_API_BASE_URL when the service URL changes. */
export const PRODUCTION_API_BASE_URL = "https://kalima-api-nyo1.onrender.com/api";

const LOCAL_API_BASE_URL = "http://localhost:8080/api";

/**
 * Resolves the Spring Boot API base URL (includes `/api` suffix).
 *
 * In the browser we always talk to the same origin the page was served from:
 * the unified reverse proxy (Caddy) forwards `/api/*` and `/ws*` to the
 * backend and everything else to Next.js. That makes localhost, a LAN IP and
 * the public Cloudflare tunnel work identically, with no hardcoded host.
 */
export function resolveApiBaseUrl(): string {
  if (typeof window !== "undefined") {
    return `${window.location.origin}/api`;
  }
  const configured = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();
  if (configured) {
    return configured;
  }
  if (process.env.VERCEL === "1" || process.env.NODE_ENV === "production") {
    return PRODUCTION_API_BASE_URL;
  }
  return LOCAL_API_BASE_URL;
}
