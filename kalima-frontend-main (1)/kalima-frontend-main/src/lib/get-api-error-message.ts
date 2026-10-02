import { ApiRequestError } from "@/lib/api/client";

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.details?.message ?? error.message ?? fallback;
  }
  return fallback;
}
