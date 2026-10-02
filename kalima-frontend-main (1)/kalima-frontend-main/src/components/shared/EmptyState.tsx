import type { LucideIcon } from "lucide-react";
import { FileSearch } from "lucide-react";
import { cn } from "@/lib/utils/cn";

interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: LucideIcon;
  action?: React.ReactNode;
  className?: string;
  /** Tightens padding/icon for use nested inside a section. */
  compact?: boolean;
}

export function EmptyState({
  title,
  description,
  icon: Icon = FileSearch,
  action,
  className,
  compact = false,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 text-center",
        compact ? "px-5 py-8" : "px-6 py-14",
        className,
      )}
    >
      <div
        className={cn(
          "flex items-center justify-center rounded-xl bg-[var(--color-surface)] text-[var(--color-navy-400)] shadow-[var(--shadow-xs)]",
          compact ? "size-10" : "size-12",
        )}
      >
        <Icon className={compact ? "size-4" : "size-5"} />
      </div>
      <h3 className="text-[0.9375rem] font-bold text-[var(--color-foreground)]">
        {title}
      </h3>
      {description ? (
        <p className="max-w-sm text-sm text-[var(--color-muted-foreground)]">
          {description}
        </p>
      ) : null}
      {action ? <div className="mt-1">{action}</div> : null}
    </div>
  );
}
