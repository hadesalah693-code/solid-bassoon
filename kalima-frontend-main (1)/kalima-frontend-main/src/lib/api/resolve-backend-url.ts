import { API_BASE_URL } from "@/lib/constants";

/** Resolve API-relative paths (e.g. `/api/messages/attachments/...`) to an absolute backend URL. */
export function resolveBackendUrl(path: string): string {
  if (path.startsWith("http://") || path.startsWith("https://")) {
    return path;
  }
  const origin = new URL(API_BASE_URL).origin;
  return path.startsWith("/") ? `${origin}${path}` : `${API_BASE_URL}/${path}`;
}
