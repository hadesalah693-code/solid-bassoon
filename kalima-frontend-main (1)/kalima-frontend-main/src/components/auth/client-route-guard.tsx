"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/auth-provider";
import { RouteGuardShell } from "@/components/auth/route-guard-shell";

/** Authenticated CLIENT users only. */
export function ClientRouteGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { loading, isAuthenticated, user } = useAuth();

  const clientReady =
    !loading &&
    isAuthenticated &&
    user != null &&
    (user.systemRole === "CLIENT" || user.systemRole === "CLIENT_USER");

  useEffect(() => {
    if (loading) return;
    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }
    if (user.systemRole !== "CLIENT" && user.systemRole !== "CLIENT_USER") {
      router.replace("/dashboard");
    }
  }, [loading, isAuthenticated, user, router]);

  const showShell = loading || !clientReady;

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
