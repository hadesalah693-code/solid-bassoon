import {
  getAccessToken,
  isTokenExpired,
} from "@/lib/auth/session";
import {
  logoutSession,
  refreshSession,
} from "@/lib/auth/auth-session-service";
import { API_BASE_URL } from "@/lib/constants";
import type { ApiError } from "@/types/common";

/**
 * Thin fetch wrapper. Adds the JWT from localStorage, sets JSON headers,
 * and normalises errors to the shared {@link ApiError} shape produced by
 * the Kalima backend's GlobalExceptionHandler.
 */
export class ApiRequestError extends Error {
  status: number;
  code: string;
  details?: ApiError;

  constructor(err: ApiError) {
    super(err?.message ?? "Request failed");
    this.status = err?.status ?? 0;
    this.code = err?.code ?? "UNKNOWN";
    this.details = err;
  }
}

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Do not attach Bearer token (login). */
  skipAuth?: boolean;
  /** Do not attempt refresh+retry on 401 (refresh/logout endpoints). */
  skipRefreshRetry?: boolean;
  /** Internal: second attempt after refresh. */
  _retried?: boolean;
};

function buildUrl(path: string, query?: RequestOptions["query"]) {
  const url = new URL(
    path.startsWith("http") ? path : `${API_BASE_URL}${path}`,
  );
  if (query) {
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") {
        url.searchParams.set(k, String(v));
      }
    });
  }
  return url.toString();
}

async function parseResponse<T>(res: Response, path: string): Promise<T> {
  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      if (res.ok) {
        return null as T;
      }
    }
  }

  if (!res.ok) {
    const errPayload = (data ?? {
      message: "Request failed",
      status: res.status,
      code: "HTTP_ERROR",
      path: path,
    }) as ApiError;
    throw new ApiRequestError(errPayload);
  }
  return data as T;
}

async function executeFetch<T>(
  path: string,
  options: RequestOptions,
): Promise<T> {
  const { body, query, headers, skipAuth, credentials, ...rest } = options;
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;

  const token = skipAuth ? null : getAccessToken();
  const res = await fetch(buildUrl(path, query), {
    ...rest,
    credentials: credentials ?? "same-origin",
    headers: {
      ...(isFormData ? {} : { "Content-Type": "application/json" }),
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(headers ?? {}),
    },
    body:
      body === undefined
        ? undefined
        : isFormData
          ? body
          : JSON.stringify(body),
  });

  return parseResponse<T>(res, path);
}

export async function request<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { skipAuth, skipRefreshRetry, _retried } = options;

  if (!skipAuth && getAccessToken() && isTokenExpired()) {
    await refreshSession();
  }

  try {
    return await executeFetch<T>(path, options);
  } catch (err) {
    const is401 =
      err instanceof ApiRequestError && err.status === 401;
    const canRetry =
      is401 &&
      !skipAuth &&
      !skipRefreshRetry &&
      !_retried &&
      !path.includes("/auth/");

    if (!canRetry) throw err;

    const refreshed = await refreshSession();
    if (!refreshed) {
      await logoutSession(true);
      throw err;
    }

    return request<T>(path, { ...options, _retried: true });
  }
}
