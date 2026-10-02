"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { LucideIcon } from "lucide-react";
import { FilePlus2, UserPlus, KeyRound } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { cn } from "@/lib/utils/cn";
import { getStoredUser } from "@/lib/auth/session";
import type { SystemRole } from "@/types/staff";

type Action = {
  label: string;
  href: string;
  icon: LucideIcon;
  accent: string;
  allowedRoles?: SystemRole[];
};

const ACTIONS: Action[] = [
  {
    label: "قضية جديدة",
    href: "/cases/new",
    icon: FilePlus2,
    accent: "bg-[var(--color-navy-50)] text-[var(--color-navy-600)]",
    allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY"],
  },
  {
    label: "عميل جديد",
    href: "/clients/new",
    icon: UserPlus,
    accent: "bg-[var(--color-gold-50)] text-[var(--color-gold-600)]",
    allowedRoles: ["ADMIN", "SECRETARY"],
  },
  {
    label: "تغيير كلمة المرور",
    href: "/account/password",
    icon: KeyRound,
    accent: "bg-[var(--color-info-bg)] text-[var(--color-info)]",
  },
];

export function QuickActions() {
  const [role, setRole] = useState<SystemRole | null>(null);
  useEffect(() => {
    setRole(getStoredUser()?.systemRole ?? null);
  }, []);

  const visible = ACTIONS.filter((a) => {
    if (!a.allowedRoles) return true;
    return role ? a.allowedRoles.includes(role) : false;
  });

  if (visible.length === 0) {
    return null;
  }

  return (
    <SectionCard
      title="إجراءات سريعة"
    >
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {visible.map((a) => (
          <Link
            key={a.label}
            href={a.href}
            className="group flex flex-col items-center gap-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-center transition-all duration-200 hover:border-[var(--color-border-strong)] hover:shadow-[var(--shadow-soft)]"
          >
            <div
              className={cn(
                "flex size-10 items-center justify-center rounded-xl transition-transform duration-200 group-hover:scale-105",
                a.accent,
              )}
            >
              <a.icon className="size-5" />
            </div>
            <span className="text-[0.8125rem] font-semibold text-[var(--color-foreground)]">
              {a.label}
            </span>
          </Link>
        ))}
      </div>
    </SectionCard>
  );
}
