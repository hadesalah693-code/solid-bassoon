"use client";

import { useEffect, useState, type ReactNode } from "react";
import { ShieldAlert } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { getStoredUser } from "@/lib/auth/session";
import type { SystemRole } from "@/types/staff";

/**
 * Mirrors the `@PreAuthorize` roles on the HR controllers:
 * reads are `ADMIN, ACCOUNTANT, SECRETARY`, writes are `ADMIN, ACCOUNTANT`.
 */
export const HR_ROLES: readonly SystemRole[] = ["ADMIN", "ACCOUNTANT", "SECRETARY"];

/** HR mutations, mirroring the backend `WRITE` guards. */
export const HR_WRITE_ROLES: readonly SystemRole[] = ["ADMIN", "ACCOUNTANT"];

export function currentSystemRole(): SystemRole | null {
  return getStoredUser()?.systemRole ?? null;
}

/** Renders `children` only for roles allowed to change HR records. */
export function HrWriteOnly({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    const role = currentSystemRole();
    setAllowed(role ? HR_WRITE_ROLES.includes(role) : false);
    setReady(true);
  }, []);

  if (!ready) return null;
  return allowed ? <>{children}</> : null;
}

export function HrRoleGate({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    const role = currentSystemRole();
    setAllowed(role ? HR_ROLES.includes(role) : false);
    setReady(true);
  }, []);

  if (!ready) {
    return <PageLoading label="جارٍ التحقق من الصلاحيات…" minHeightClassName="min-h-[12rem]" />;
  }
  if (!allowed) {
    return (
      <EmptyState
        icon={ShieldAlert}
        title="ليست لديك صلاحية للوصول إلى الموارد البشرية"
        description="هذه الشاشة متاحة للمحاسب والسكرتير ومدير النظام فقط. تواصل مع مدير النظام إن كنت تحتاج إلى الوصول."
      />
    );
  }
  return <>{children}</>;
}