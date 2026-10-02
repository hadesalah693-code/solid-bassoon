"use client";

import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import { motion } from "framer-motion";

import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils/cn";

interface StatCardProps {
  label: string;
  /** Optional short helper under the label (e.g. مصدر البيانات). */
  hint?: string;
  value: number | string;
  trend?: number;
  /** Pre-rendered icon element (e.g. `<Briefcase />`). Kept as a ReactNode so
   *  server components can pass it safely across the RSC boundary. */
  icon: React.ReactNode;
  accent?: "navy" | "gold" | "success" | "danger";
  delay?: number;
}

const ACCENT: Record<Required<StatCardProps>["accent"], string> = {
  navy: "bg-[var(--color-navy-50)] text-[var(--color-navy-700)]",
  gold: "bg-[var(--color-gold-50)] text-[var(--color-gold-600)]",
  success: "bg-[var(--color-success-bg)] text-[var(--color-success)]",
  danger: "bg-[var(--color-danger-bg)] text-[var(--color-danger)]",
};

export function StatCard({
  label,
  hint,
  value,
  trend,
  icon,
  accent = "navy",
  delay = 0,
}: StatCardProps) {
  const trendPositive = (trend ?? 0) >= 0;
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay }}
    >
      <Card className="overflow-hidden border-[var(--color-border-strong)]/35 bg-[var(--color-surface)] p-5 shadow-[var(--shadow-soft)] ring-1 ring-[var(--color-border)]/40 transition-[border-color,box-shadow] hover:border-[var(--color-gold-400)]/25 hover:shadow-[var(--shadow-pop)]">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-[13px] font-semibold leading-tight text-[var(--color-foreground)]">
              {label}
            </p>
            {hint ? (
              <p className="mt-0.5 text-[11px] leading-snug text-[var(--color-muted-foreground)]">
                {hint}
              </p>
            ) : null}
            <p className="mt-2 text-3xl font-bold tabular-nums tracking-tight text-[var(--color-foreground)] ltr-nums">
              {value}
            </p>
          </div>
          <div
            className={cn(
              "flex size-12 shrink-0 items-center justify-center rounded-2xl [&_svg]:size-5",
              ACCENT[accent],
            )}
          >
            {icon}
          </div>
        </div>
        {typeof trend === "number" ? (
          <div className="mt-3 flex items-center gap-1.5 text-xs">
            <span
              className={cn(
                "inline-flex items-center gap-0.5 rounded-md px-1.5 py-0.5 font-medium",
                trendPositive
                  ? "bg-[var(--color-success-bg)] text-[var(--color-success)]"
                  : "bg-[var(--color-danger-bg)] text-[var(--color-danger)]",
              )}
            >
              {trendPositive ? (
                <ArrowUpRight className="size-3.5" />
              ) : (
                <ArrowDownRight className="size-3.5" />
              )}
              <span className="ltr-nums">
                {trendPositive ? "+" : ""}
                {trend}
              </span>
            </span>
            <span className="text-[var(--color-muted-foreground)]">
              هذا الأسبوع
            </span>
          </div>
        ) : null}
      </Card>
    </motion.div>
  );
}
