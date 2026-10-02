"use client";

import { SlidersHorizontal } from "lucide-react";

import { SearchInput } from "@/components/shared/SearchInput";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";
import type { SystemRole } from "@/types/staff";

export type StaffRoleFilter = "ALL" | SystemRole;

interface StaffFiltersProps {
  showSearch?: boolean;
  search: string;
  onSearchChange: (value: string) => void;
  role: StaffRoleFilter;
  onRoleChange: (value: StaffRoleFilter) => void;
}

const ROLES: { value: StaffRoleFilter; label: string }[] = [
  { value: "ALL", label: "الكل" },
  { value: "ADMIN", label: "مدير" },
  { value: "SENIOR_LAWYER", label: "محامي أول" },
  { value: "LAWYER", label: "محامي" },
  { value: "SECRETARY", label: "سكرتير" },
  { value: "ACCOUNTANT", label: "محاسب" },
];

export function StaffFilters({
  showSearch = true,
  search,
  onSearchChange,
  role,
  onRoleChange,
}: StaffFiltersProps) {
  return (
    <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
      {showSearch ? (
        <SearchInput
          value={search}
          onChange={onSearchChange}
          placeholder="ابحث بالاسم أو البريد..."
          className="lg:max-w-sm"
        />
      ) : (
        <p className="max-w-xl text-xs text-[var(--color-muted-foreground)]">
          البحث النصي ليس مدعوماً من الـ API حالياً (لا يوجد معامل
          <code className="mx-0.5 rounded bg-[var(--color-surface-muted)] px-1 ltr-nums">search</code>{" "}
          في <code className="rounded bg-[var(--color-surface-muted)] px-1">GET /api/staff</code>).
          استخدم فلتر الدور والصفحات.
        </p>
      )}
      <div className="flex items-center gap-2 overflow-x-auto">
        <div className="flex items-center gap-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-1">
          {ROLES.map((r) => (
            <button
              key={r.value}
              onClick={() => onRoleChange(r.value)}
              className={cn(
                "whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-medium transition-colors",
                role === r.value
                  ? "bg-[var(--color-navy-800)] text-white"
                  : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
              )}
            >
              {r.label}
            </button>
          ))}
        </div>
        <Button variant="outline" size="md">
          <SlidersHorizontal className="size-4" />
          خيارات
        </Button>
      </div>
    </div>
  );
}
