import type { CurrentUser } from "@/types/user";

/** Matches `LoginRequest` in `kalima-backend` (`email`, `password`). */
export type LoginRequest = { email: string; password: string };

/**
 * Matches `com.kalima.backend.auth.dto.LoginResponse`:
 * `accessToken`, `tokenType`, `expiresAt` (ISO-8601), `person`.
 */
export type LoginResponse = {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  person: CurrentUser;
};
