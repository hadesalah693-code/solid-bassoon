"use client";

import { cn } from "@/lib/utils/cn";
import { ocrStatusLabel } from "@/lib/ocr/labels";
import type { OcrStatus } from "@/types/ocr";

const TONE: Record<
  OcrStatus,
  { badge: string; dot: string }
> = {
  PENDING: {
    badge:
      "bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)] ring-[var(--color-border)]",
    dot: "bg-[var(--color-muted-foreground)]",
  },
  PROCESSING: {
    badge:
      "bg-[var(--color-info-bg)] text-[var(--color-info)] ring-[var(--color-info)]/20",
    dot: "bg-[var(--color-info)] animate-pulse",
  },
  COMPLETED: {
    badge:
      "bg-[var(--color-success-bg)] text-[var(--color-success)] ring-[var(--color-success)]/15",
    dot: "bg-[var(--color-success)]",
  },
  FAILED: {
    badge:
      "bg-[var(--color-danger-bg)] text-[var(--color-danger)] ring-[var(--color-danger)]/12",
    dot: "bg-[var(--color-danger)]",
  },
  CANCELLED: {
    badge:
      "bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)] ring-[var(--color-border)]",
    dot: "bg-[var(--color-muted-foreground)]",
  },
};

type Props = {
  status: OcrStatus;
  className?: string;
  compact?: boolean;
};

export function OcrStatusBadge({ status, className, compact }: Props) {
  const tone = TONE[status];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset",
        tone.badge,
        compact && "px-2 py-0 text-[11px]",
        className,
      )}
    >
      <span className={cn("size-1.5 shrink-0 rounded-full", tone.dot)} aria-hidden />
      {ocrStatusLabel(status)}
    </span>
  );
}
