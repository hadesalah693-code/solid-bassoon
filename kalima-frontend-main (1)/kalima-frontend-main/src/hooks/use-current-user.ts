"use client";

import { useAuth } from "@/components/auth/auth-provider";

/**
 * Client-only: current user from {@link AuthProvider} (post-bootstrap).
 */
export function useCurrentUser() {
  const { user, loading } = useAuth();
  return { user, ready: !loading, setUser: () => {} } as const;
}
