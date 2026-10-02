import type { CurrentUser } from "@/types/user";

/** Default home after login / root, based on `systemRole`. */
export function getDefaultRouteForUser(user: CurrentUser | null | undefined) {
  if (!user) return "/login";
  if (user.systemRole === "CLIENT" || user.systemRole === "CLIENT_USER") {
    return "/client/dashboard";
  }
  return "/dashboard";
}
