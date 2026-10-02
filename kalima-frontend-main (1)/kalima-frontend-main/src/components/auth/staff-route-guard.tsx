"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/auth-provider";
import { getDefaultRouteForUser } from "@/lib/auth/routes";
import { RouteGuardShell } from "@/components/auth/route-guard-shell";

/**
 * Authenticated staff only; CLIENT users go to the client portal.
 */
export function StaffRouteGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { loading, isAuthenticated, user } = useAuth();

  const staffReady =
    !loading &&
    isAuthenticated &&
    user != null &&
    user.systemRole !== "CLIENT";

  useEffect(() => {
    if (loading) return;
    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }
    if (user.systemRole === "CLIENT") {
      router.replace(getDefaultRouteForUser(user));
    }
  }, [loading, isAuthenticated, user, router]);

  const showShell = loading || !staffReady;

  return (
    <>
      {showShell ? (
        <div className="fixed inset-0 z-[100] bg-[var(--color-background)]">
          <RouteGuardShell
            label={loading ? undefined : "جارٍ التوجيه…"}
          />
        </div>
      ) : null}
      {children}
    </>
  );
}
