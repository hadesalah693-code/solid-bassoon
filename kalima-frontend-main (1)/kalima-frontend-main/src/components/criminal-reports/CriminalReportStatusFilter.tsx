"use client";

import { cn } from "@/lib/utils/cn";
import { CRIMINAL_REPORT_STATUS_AR } from "@/lib/criminal-reports/labels";
import type { CriminalReportStatusDto } from "@/types/criminal-report";

const STATUS_OPTIONS: (CriminalReportStatusDto | "")[] = [
  "",
  "UNDER_INVESTIGATION",
  "REFERRAL",
  "ARCHIVED",
  "FINAL_ARCHIVED",
];

type Props = {
  value: CriminalReportStatusDto | "";
  onChange: (value: CriminalReportStatusDto | "") => void;
  counts?: Partial<Record<CriminalReportStatusDto | "all", number>>;
};

export function CriminalReportStatusFilter({ value, onChange, counts }: Props) {
  return (
    <div
      className="inline-flex w-full flex-wrap gap-1 rounded-xl bg-[var(--color-surface-muted)] p-1 ring-1 ring-[var(--color-border)]/60 sm:w-auto"
      role="group"
      aria-label="تصفية حسب الحالة"
    >
      {STATUS_OPTIONS.map((status) => {
        const active = value === status;
        const label = status ? CRIMINAL_REPORT_STATUS_AR[status] : "الكل";
        const countKey = status || "all";
        const count = counts?.[countKey];
        return (
          <button
            key={status || "all"}
            type="button"
            onClick={() => onChange(status)}
            className={cn(
              "inline-flex min-h-9 flex-1 items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition sm:flex-none",
              active
                ? "bg-[var(--color-surface)] text-[var(--color-foreground)] shadow-[var(--shadow-soft)]"
                : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
            )}
          >
            <span>{label}</span>
            {count != null ? (
              <span
                className={cn(
                  "rounded-md px-1.5 py-0.5 text-[10px] font-bold tabular-nums ltr-nums",
                  active
                    ? "bg-[var(--color-gold-50)] text-[var(--color-gold-700)]"
                    : "bg-[var(--color-muted)]/50",
                )}
              >
                {count.toLocaleString("ar-EG")}
              </span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
}
