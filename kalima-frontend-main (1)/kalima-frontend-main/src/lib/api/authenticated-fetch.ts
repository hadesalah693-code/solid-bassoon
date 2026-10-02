import { getAccessToken, isTokenExpired } from "@/lib/auth/session";
import {
  logoutSession,
  refreshSession,
} from "@/lib/auth/auth-session-service";
import { API_BASE_URL } from "@/lib/constants";
import type { ApiError } from "@/types/common";
import { ApiRequestError } from "@/lib/api/client";

export type AuthenticatedFetchInit = RequestInit & {
  skipRefreshRetry?: boolean;
  _retried?: boolean;
};

function resolveUrl(pathOrUrl: string): string {
  if (pathOrUrl.startsWith("http")) return pathOrUrl;
  const path = pathOrUrl.startsWith("/") ? pathOrUrl : `/${pathOrUrl}`;
  return `${API_BASE_URL}${path}`;
}

async function parseAuthenticatedResponse(
  res: Response,
  path: string,
): Promise<Response> {
  if (res.ok) return res;
  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      /* ignore */
    }
  }
  throw new ApiRequestError(
    (data ?? {
      message: "Request failed",
      status: res.status,
      code: "HTTP_ERROR",
      path,
    }) as ApiError,
  );
}

/**
 * Fetch with Bearer token + single refresh retry on 401.
 * Use for FormData / blob paths that bypass {@link request}.
 */
export async function authenticatedFetch(
  pathOrUrl: string,
  init: AuthenticatedFetchInit = {},
): Promise<Response> {
  const { skipRefreshRetry, _retried, headers, ...rest } = init;
  const url = resolveUrl(pathOrUrl);
  const path = url.replace(API_BASE_URL, "") || pathOrUrl;

  if (getAccessToken() && isTokenExpired()) {
    await refreshSession();
  }

  const token = getAccessToken();
  const run = () =>
    fetch(url, {
      ...rest,
      credentials: rest.credentials ?? "same-origin",
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(headers ?? {}),
      },
    });

  let res = await run();

  if (
    res.status === 401 &&
    !skipRefreshRetry &&
    !_retried &&
    !path.includes("/auth/")
  ) {
    const refreshed = await refreshSession();
    if (!refreshed) {
      await logoutSession(true);
      return res;
    }
    const newToken = getAccessToken();
    res = await fetch(url, {
      ...rest,
      credentials: rest.credentials ?? "same-origin",
      headers: {
        ...(newToken ? { Authorization: `Bearer ${newToken}` } : {}),
        ...(headers ?? {}),
      },
    });
  }

  return parseAuthenticatedResponse(res, path);
}
