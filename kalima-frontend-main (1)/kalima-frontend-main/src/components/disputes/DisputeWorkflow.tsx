"use client";

import { Check, Circle } from "lucide-react";

import { cn } from "@/lib/utils/cn";
import { DISPUTE_WORKFLOW_ORDER, DISPUTE_WORKFLOW_STEP_AR } from "@/lib/disputes/labels";
import type { DisputeWorkflowStepDto } from "@/types/dispute";

export type DisputeWorkflowProps = {
  currentStep: DisputeWorkflowStepDto;
  className?: string;
};

export function DisputeWorkflow({ currentStep, className }: DisputeWorkflowProps) {
  const idx = DISPUTE_WORKFLOW_ORDER.indexOf(currentStep);
  const safeIdx = idx >= 0 ? idx : 0;

  return (
    <div
      dir="rtl"
      className={cn(
        "rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm",
        className,
      )}
    >
      <h3 className="mb-4 text-sm font-semibold text-[var(--color-foreground)]">
        مسار سير العمل
      </h3>
      <ol className="flex flex-col gap-0 sm:gap-1">
        {DISPUTE_WORKFLOW_ORDER.map((step, i) => {
          const done = i < safeIdx;
          const active = i === safeIdx;
          const label = DISPUTE_WORKFLOW_STEP_AR[step];
          return (
            <li key={step} className="flex items-start gap-3">
              <span
                className={cn(
                  "mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full border text-xs font-medium transition-colors",
                  done &&
                    "border-emerald-500/50 bg-emerald-500/15 text-emerald-700 dark:text-emerald-300",
                  active &&
                    "border-[var(--color-gold-400)] bg-[var(--color-gold-400)]/20 text-[var(--color-foreground)] shadow-[0_0_0_3px_rgba(212,175,55,0.2)]",
                  !done &&
                    !active &&
                    "border-[var(--color-border)] bg-[var(--color-muted)]/40 text-[var(--color-muted-foreground)]",
                )}
                aria-hidden
              >
                {done ? (
                  <Check className="size-4 text-emerald-600 dark:text-emerald-400" />
                ) : active ? (
                  <span className="size-2.5 rounded-full bg-[var(--color-gold-400)]" />
                ) : (
                  <Circle className="size-4 opacity-45" />
                )}
              </span>
              <div className="min-w-0 flex-1 pb-3 sm:pb-2">
                <p
                  className={cn(
                    "text-sm leading-snug",
                    active && "font-semibold text-[var(--color-foreground)]",
                    done &&
                      "font-medium text-[var(--color-foreground)]/90",
                    !done &&
                      !active &&
                      "text-[var(--color-muted-foreground)]",
                  )}
                >
                  {label}
                </p>
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
