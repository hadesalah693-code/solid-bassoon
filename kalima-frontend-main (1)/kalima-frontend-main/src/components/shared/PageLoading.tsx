import { Loader2 } from "lucide-react";

import { cn } from "@/lib/utils/cn";

type PageLoadingProps = {
  label: string;
  className?: string;
  minHeightClassName?: string;
  "aria-label"?: string;
};

export function PageLoading({
  label,
  className,
  minHeightClassName = "min-h-[12rem]",
  "aria-label": ariaLabel,
}: PageLoadingProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-3",
        minHeightClassName,
        "text-sm text-[var(--color-muted-foreground)]",
        className,
      )}
      role="status"
      aria-live="polite"
      aria-busy="true"
      aria-label={ariaLabel ?? label}
    >
      <Loader2 className="size-7 shrink-0 animate-spin text-[var(--color-gold-400)]" aria-hidden />
      <p className="text-[0.8125rem]">{label}</p>
    </div>
  );
}

type DashboardPageSkeletonProps = {
  className?: string;
};

export function DashboardPageSkeleton({ className }: DashboardPageSkeletonProps) {
  return (
    <div
      className={cn("space-y-6", className)}
      role="status"
      aria-live="polite"
      aria-busy="true"
      aria-label="جارٍ تحميل لوحة التحكم"
    >
      <div className="h-40 animate-pulse rounded-2xl bg-[var(--color-surface-muted)]" />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div
            key={i}
            className="h-24 animate-pulse rounded-2xl bg-[var(--color-surface-muted)]"
          />
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <div className="h-48 animate-pulse rounded-2xl bg-[var(--color-surface-muted)]" />
          <div className="h-24 animate-pulse rounded-2xl bg-[var(--color-surface-muted)]/60" />
        </div>
        <div className="h-64 animate-pulse rounded-2xl bg-[var(--color-surface-muted)]" />
      </div>
    </div>
  );
}
