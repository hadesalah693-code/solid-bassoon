import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils/cn";

const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-lg border px-2 py-0.5 text-[0.6875rem] font-semibold leading-5 transition-colors",
  {
    variants: {
      tone: {
        neutral:
          "border-[var(--color-border)] bg-[var(--color-surface-muted)] text-[var(--color-foreground-secondary)]",
        navy: "border-[var(--color-navy-200)] bg-[var(--color-navy-50)] text-[var(--color-navy-700)]",
        gold: "border-[var(--color-gold-200)] bg-[var(--color-gold-50)] text-[var(--color-gold-600)]",
        success:
          "border-[var(--color-success-border)] bg-[var(--color-success-bg)] text-[var(--color-success)]",
        warning:
          "border-[var(--color-warning-border)] bg-[var(--color-warning-bg)] text-[var(--color-warning)]",
        danger:
          "border-[var(--color-danger-border)] bg-[var(--color-danger-bg)] text-[var(--color-danger)]",
        info: "border-[var(--color-info-border)] bg-[var(--color-info-bg)] text-[var(--color-info)]",
      },
    },
    defaultVariants: { tone: "neutral" },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, tone, ...props }: BadgeProps) {
  return (
    <span className={cn(badgeVariants({ tone }), className)} {...props} />
  );
}
