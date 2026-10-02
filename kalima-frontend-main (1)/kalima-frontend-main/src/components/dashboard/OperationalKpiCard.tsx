"use client";

import { motion } from "framer-motion";

import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils/cn";

export type OperationalKpiAccent = "navy" | "gold" | "success" | "danger" | "info";

const ACCENT: Record<
  OperationalKpiAccent,
  { icon: string; bar: string }
> = {
  navy: {
    icon: "bg-[var(--color-navy-50)] text-[var(--color-navy-700)]",
    bar: "bg-[var(--color-navy-400)]",
  },
  gold: {
    icon: "bg-[var(--color-gold-50)] text-[var(--color-gold-600)]",
    bar: "bg-[var(--color-gold-400)]",
  },
  success: {
    icon: "bg-[var(--color-success-bg)] text-[var(--color-success)]",
    bar: "bg-[var(--color-success)]",
  },
  danger: {
    icon: "bg-[var(--color-danger-bg)] text-[var(--color-danger)]",
    bar: "bg-[var(--color-danger)]",
  },
  info: {
    icon: "bg-[var(--color-info-bg)] text-[var(--color-info)]",
    bar: "bg-[var(--color-info)]",
  },
};

type OperationalKpiCardProps = {
  label: string;
  value: number | string;
  hint?: string;
  icon: React.ReactNode;
  accent?: OperationalKpiAccent;
  delay?: number;
  valueLoading?: boolean;
};

export function OperationalKpiCard({
  label,
  value,
  hint,
  icon,
  accent = "navy",
  delay = 0,
  valueLoading = false,
}: OperationalKpiCardProps) {
  const tones = ACCENT[accent];
  return (
    <motion.div
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay }}
    >
      <Card
        className={cn(
          "relative overflow-hidden border-[var(--color-border)] bg-[var(--color-surface)] p-4",
          "transition-[border-color,box-shadow] duration-200",
          "hover:border-[var(--color-border-strong)] hover:shadow-[var(--shadow-soft)]",
        )}
      >
        <span
          className={cn(
            "absolute inset-x-0 top-0 h-[2px]",
            tones.bar,
          )}
          aria-hidden
        />
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1 space-y-1">
            <p className="text-[0.6875rem] font-bold uppercase tracking-wider text-[var(--color-muted-foreground)]">
              {label}
            </p>
            {valueLoading ?
              <div
                className="h-8 w-20 max-w-full animate-pulse rounded-lg bg-[var(--color-surface-muted)]"
                aria-hidden
              />
            : <p className="tabular-nums text-2xl font-extrabold leading-none tracking-tight text-[var(--color-foreground)] ltr-nums sm:text-[1.625rem]">
                {value}
              </p>}
            {hint ?
              <p className="text-[0.6875rem] leading-snug text-[var(--color-muted-foreground)]">
                {hint}
              </p>
            : null}
          </div>
          <div
            className={cn(
              "flex size-10 shrink-0 items-center justify-center rounded-xl [&_svg]:size-[1.125rem]",
              tones.icon,
            )}
          >
            {icon}
          </div>
        </div>
      </Card>
    </motion.div>
  );
}
