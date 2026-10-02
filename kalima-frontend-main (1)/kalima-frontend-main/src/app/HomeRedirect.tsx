"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/auth-provider";
import { getDefaultRouteForUser } from "@/lib/auth/routes";

export function HomeRedirect() {
  const router = useRouter();
  const { loading, isAuthenticated, user } = useAuth();

  useEffect(() => {
    if (loading) return;
    if (!isAuthenticated) {
      router.replace("/login");
      return;
    }
    router.replace(getDefaultRouteForUser(user));
  }, [loading, isAuthenticated, user, router]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--color-background)]">
      <p className="text-sm text-[var(--color-muted-foreground)]">جارٍ التوجيه…</p>
    </div>
  );
}
