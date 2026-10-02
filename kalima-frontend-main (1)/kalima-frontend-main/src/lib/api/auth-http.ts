import { API_BASE_URL } from "@/lib/constants";
import type { ApiError } from "@/types/common";
import type { LoginRequest, LoginResponse } from "@/lib/api/auth-types";

/**
 * Auth endpoints use raw fetch (not {@link request}) to avoid circular imports
 * with the refresh interceptor in the API client.
 */
type AuthFetchInit = Omit<RequestInit, "body"> & { body?: unknown };

async function authFetch<T>(
  path: string,
  init: AuthFetchInit = {},
): Promise<T> {
  const { body, headers, ...rest } = init;
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...(headers ?? {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      if (res.ok) return null as T;
    }
  }

  if (!res.ok) {
    const errPayload = (data ?? {
      message: "Request failed",
      status: res.status,
      code: "HTTP_ERROR",
      path,
    }) as ApiError;
    throw Object.assign(new Error(errPayload.message ?? "Request failed"), {
      status: errPayload.status,
      code: errPayload.code,
      details: errPayload,
    });
  }

  return data as T;
}

export async function loginRequest(body: LoginRequest): Promise<LoginResponse> {
  return authFetch<LoginResponse>("/auth/login", { method: "POST", body });
}

export async function refreshRequest(): Promise<LoginResponse> {
  return authFetch<LoginResponse>("/auth/refresh", { method: "POST" });
}

export async function logoutRequest(): Promise<void> {
  return authFetch<void>("/auth/logout", { method: "POST" });
}
