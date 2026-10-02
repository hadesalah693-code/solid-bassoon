"use client";

import { useEffect, useState, type ReactNode } from "react";
import { ShieldAlert } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { getStoredUser } from "@/lib/auth/session";
import type { SystemRole } from "@/types/staff";

/**
 * Read access to the ledger, mirroring the backend's read endpoints
 * (`ROLES` in the accounting controllers).
 */
export const ACCOUNTING_ROLES: readonly SystemRole[] = [
  "ADMIN",
  "ACCOUNTANT",
  "SENIOR_LAWYER",
];

/** Alias used by the sidebar; keep the two in sync through this export. */
export const ACCOUNTING_READ_ROLES = ACCOUNTING_ROLES;

/** Ledger mutations, mirroring the backend's `WRITE_ROLES`. */
export const ACCOUNTING_WRITE_ROLES: readonly SystemRole[] = ["ADMIN", "ACCOUNTANT"];

export function currentSystemRole(): SystemRole | null {
  return getStoredUser()?.systemRole ?? null;
}

export function canWriteAccounting(): boolean {
  const role = currentSystemRole();
  return role ? ACCOUNTING_WRITE_ROLES.includes(role) : false;
}

/**
 * Renders `children` only for roles allowed to post to the ledger. Read-only
 * viewers still get the reports and lists, just without the mutating actions.
 */
export function AccountingWriteOnly({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    setAllowed(canWriteAccounting());
    setReady(true);
  }, []);

  if (!ready) return null;
  return allowed ? <>{children}</> : null;
}

export function AccountingRoleGate({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    const role = getStoredUser()?.systemRole;
    setAllowed(role ? ACCOUNTING_ROLES.includes(role) : false);
    setReady(true);
  }, []);

  if (!ready) {
    return <PageLoading label="جارٍ التحقق من الصلاحيات…" minHeightClassName="min-h-[12rem]" />;
  }
  if (!allowed) {
    return (
      <EmptyState
        icon={ShieldAlert}
        title="ليست لديك صلاحية للوصول إلى المحاسبة"
        description="هذه الشاشة متاحة للمحاسب والمحامي الرئيسي فقط. تواصل مع مدير النظام إن كنت تحتاج إلى الوصول."
      />
    );
  }
  return <>{children}</>;
}
