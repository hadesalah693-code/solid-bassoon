"use client";

import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils/cn";

type DisputeModuleHeroProps = {
  eyebrow?: string;
  title: string;
  description?: React.ReactNode;
  icon?: LucideIcon;
  badges?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
};

export function DisputeModuleHero({
  eyebrow,
  title,
  description,
  icon: Icon,
  badges,
  actions,
  className,
}: DisputeModuleHeroProps) {
  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-3xl border border-[var(--color-navy-800)]/50",
        "bg-gradient-to-bl from-[var(--color-navy-900)] via-[var(--color-navy-800)] to-[var(--color-navy-950)]",
        "px-5 py-6 shadow-[var(--shadow-pop)] sm:px-7 sm:py-7",
        className,
      )}
    >
      <div
        className="pointer-events-none absolute -start-20 -top-16 size-[16rem] rounded-full bg-[var(--color-gold-400)]/10 blur-3xl"
        aria-hidden
      />
      <div
        className="pointer-events-none absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-[var(--color-gold-400)]/28 to-transparent"
        aria-hidden
      />

      <div className="relative flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          {Icon ?
            <div
              className={cn(
                "flex size-12 shrink-0 items-center justify-center rounded-2xl",
                "bg-[var(--color-gold-400)]/15 text-[var(--color-gold-300)] ring-1 ring-[var(--color-gold-400)]/25",
              )}
            >
              <Icon className="size-6" aria-hidden />
            </div>
          : null}
          <div className="min-w-0 flex-1 space-y-2">
            {eyebrow ?
              <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--color-gold-300)]">
                {eyebrow}
              </p>
            : null}
            <h1 className="text-xl font-bold tracking-tight text-white sm:text-2xl">{title}</h1>
            {description ?
              <div className="max-w-3xl text-sm leading-relaxed text-white/80">{description}</div>
            : null}
            {badges ?
              <div className="flex flex-wrap items-center gap-2 pt-0.5">{badges}</div>
            : null}
          </div>
        </div>
        {actions ?
          <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>
        : null}
      </div>
    </div>
  );
}
