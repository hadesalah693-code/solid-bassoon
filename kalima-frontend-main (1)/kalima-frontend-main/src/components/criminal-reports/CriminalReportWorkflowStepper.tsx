"use client";

import { Check } from "lucide-react";

import { cn } from "@/lib/utils/cn";
import {
  CRIMINAL_REPORT_STATUS_AR,
  CRIMINAL_REPORT_WORKFLOW_STEPS,
} from "@/lib/criminal-reports/labels";
import type { CriminalReportStatusDto } from "@/types/criminal-report";

type Props = {
  status: CriminalReportStatusDto;
  className?: string;
};

const STATUS_ORDER = CRIMINAL_REPORT_WORKFLOW_STEPS.map((s) => s.status);

function stepIndex(status: CriminalReportStatusDto) {
  return STATUS_ORDER.indexOf(status);
}

export function CriminalReportWorkflowStepper({ status, className }: Props) {
  const activeIdx = stepIndex(status);

  return (
    <div
      className={cn(
        "rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-[var(--shadow-soft)] ring-1 ring-[var(--color-border)]/50 sm:p-5",
        className,
      )}
    >
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-[var(--color-foreground)]">مسار البلاغ</p>
        <span className="rounded-lg bg-[var(--color-navy-50)] px-2.5 py-1 text-xs font-medium text-[var(--color-navy-800)] ring-1 ring-[var(--color-navy-200)]/60">
          {CRIMINAL_REPORT_STATUS_AR[status]}
        </span>
      </div>
      <ol className="grid gap-4 sm:grid-cols-4">
        {CRIMINAL_REPORT_WORKFLOW_STEPS.map((step, idx) => {
          const done = idx < activeIdx;
          const active = idx === activeIdx;
          return (
            <li key={step.status} className="relative min-w-0">
              {idx < CRIMINAL_REPORT_WORKFLOW_STEPS.length - 1 ? (
                <span
                  className={cn(
                    "pointer-events-none absolute start-[calc(50%+1.25rem)] top-5 hidden h-0.5 w-[calc(100%-2.5rem)] sm:block",
                    done ? "bg-[var(--color-gold-400)]" : "bg-[var(--color-border)]",
                  )}
                  aria-hidden
                />
              ) : null}
              <div className="flex flex-col items-center gap-2 text-center">
                <span
                  className={cn(
                    "flex size-10 items-center justify-center rounded-full text-sm font-bold ring-2 transition",
                    done && "bg-[var(--color-gold-400)] text-[var(--color-navy-950)] ring-[var(--color-gold-400)]",
                    active && "bg-[var(--color-navy-900)] text-[var(--color-gold-300)] ring-[var(--color-gold-400)] shadow-[var(--shadow-soft)]",
                    !done && !active && "bg-[var(--color-muted)]/40 text-[var(--color-muted-foreground)] ring-[var(--color-border)]",
                  )}
                >
                  {done ? <Check className="size-4" aria-hidden /> : idx + 1}
                </span>
                <div className="min-w-0">
                  <p
                    className={cn(
                      "text-sm font-semibold",
                      active ? "text-[var(--color-foreground)]" : "text-[var(--color-muted-foreground)]",
                    )}
                  >
                    {step.label}
                  </p>
                  {step.caption ? (
                    <p className="mt-0.5 text-[11px] leading-snug text-[var(--color-muted-foreground)]">
                      {step.caption}
                    </p>
                  ) : null}
                </div>
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
