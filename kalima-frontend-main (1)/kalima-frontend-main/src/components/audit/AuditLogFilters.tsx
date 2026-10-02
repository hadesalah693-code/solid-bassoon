"use client";

import { Filter } from "lucide-react";
import { SearchInput } from "@/components/shared/SearchInput";
import { Button } from "@/components/ui/button";

interface AuditLogFiltersProps {
  search: string;
  onSearchChange: (value: string) => void;
  status: "all" | "success" | "failed";
  onStatusChange: (value: "all" | "success" | "failed") => void;
}

const STATUSES: { value: "all" | "success" | "failed"; label: string }[] = [
  { value: "all", label: "الكل" },
  { value: "success", label: "ناجحة" },
  { value: "failed", label: "فاشلة" },
];

export function AuditLogFilters({
  search,
  onSearchChange,
  status,
  onStatusChange,
}: AuditLogFiltersProps) {
  return (
    <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
      <SearchInput
        value={search}
        onChange={onSearchChange}
        placeholder="بحث حسب الإجراء أو البريد أو IP..."
        className="md:max-w-md"
      />
      <div className="flex items-center gap-2">
        <div className="flex items-center gap-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-1">
          {STATUSES.map((s) => (
            <button
              key={s.value}
              onClick={() => onStatusChange(s.value)}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                status === s.value
                  ? "bg-[var(--color-navy-800)] text-white"
                  : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>
        <Button variant="outline" size="md">
          <Filter className="size-4" />
          فلترة متقدمة
        </Button>
      </div>
    </div>
  );
}
