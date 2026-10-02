"use client";

import Link from "next/link";
import { Briefcase, ClipboardList, FolderOpen } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";

export type DashboardHeroOpsMetrics = {
  activeCases: number;
  hearingsToday: number;
  pendingTasks: number;
  disputePathsHot: number;
};

type DashboardHeroProps = {
  greeting: string;
  canCreateCase: boolean;
  ops: DashboardHeroOpsMetrics | null;
  disputeHotPending?: boolean;
};

export function DashboardHero({
  greeting,
  canCreateCase,
  ops,
  disputeHotPending,
}: DashboardHeroProps) {
  const cells =
    ops ?
      [
        { label: "قضايا نشطة", value: ops.activeCases },
        { label: "جلسات اليوم", value: ops.hearingsToday },
        { label: "مهام مفتوحة", value: ops.pendingTasks },
        { label: "مراحل جارية", value: ops.disputePathsHot },
      ]
    : null;

  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-2xl border border-[var(--color-navy-700)]/40",
        "bg-[var(--color-navy-900)]",
        "px-5 py-6 sm:px-7 sm:py-7",
      )}
    >
      <div
        className="pointer-events-none absolute -start-20 -top-16 size-[20rem] rounded-full bg-[var(--color-gold-400)]/[0.06] blur-3xl"
        aria-hidden
      />
      <div
        className="pointer-events-none absolute -end-16 bottom-0 size-[14rem] rounded-full bg-[var(--color-navy-600)]/20 blur-3xl"
        aria-hidden
      />

      <div className="relative flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
        <div className="min-w-0 flex-1 space-y-2">
          <p className="text-[0.6875rem] font-bold uppercase tracking-[0.2em] text-[var(--color-gold-400)]">
            مركز التشغيل
          </p>
          <h1 className="text-xl font-extrabold tracking-tight text-white sm:text-2xl">
            لوحة التحكم
          </h1>
          <p className="max-w-xl text-[0.8125rem] leading-relaxed text-white/60">
            نظرة تشغيلية على القضايا والجلسات والمهام ومسارات النزاع.
          </p>
          <p className="text-[0.8125rem] font-semibold text-[var(--color-gold-300)]">{greeting}</p>
        </div>

        <div className="flex w-full shrink-0 flex-col gap-4 lg:max-w-md xl:max-w-sm">
          <div className="flex flex-wrap gap-2">
            {canCreateCase ?
              <Button variant="gold" size="sm" asChild>
                <Link href="/cases/new" className="inline-flex items-center gap-2">
                  <Briefcase className="size-4 shrink-0" aria-hidden />
                  قضية جديدة
                </Link>
              </Button>
            : null}
            <Button size="sm" variant="outline" asChild className="border-white/15 bg-white/[0.06] text-white hover:bg-white/10 hover:text-white hover:border-white/20">
              <Link href="/tasks" className="inline-flex items-center gap-2">
                <ClipboardList className="size-4 shrink-0" aria-hidden />
                مهمة جديدة
              </Link>
            </Button>
            <Button size="sm" variant="outline" asChild className="border-white/15 bg-white/[0.06] text-white hover:bg-white/10 hover:text-white hover:border-white/20">
              <Link href="/cases" className="inline-flex items-center gap-2">
                <FolderOpen className="size-4 shrink-0" aria-hidden />
                عرض القضايا
              </Link>
            </Button>
          </div>

          {cells ?
            <div
              className="grid grid-cols-2 gap-2 sm:grid-cols-4"
              dir="rtl"
            >
              {cells.map((c) => (
                <div
                  key={c.label}
                  className="min-w-0 rounded-xl border border-white/[0.08] bg-white/[0.04] px-3 py-2.5 text-center"
                >
                  <p className="text-[0.625rem] font-bold uppercase tracking-wider text-white/40">
                    {c.label}
                  </p>
                  {disputeHotPending && c.label === "مراحل جارية" ?
                    <div
                      className="mx-auto mt-2 h-7 w-[2.85rem] animate-pulse rounded-lg bg-white/10"
                      aria-hidden
                      role="presentation"
                    />
                  : <p className="mt-1 font-mono text-lg font-extrabold tabular-nums text-white ltr-nums">
                      {c.value}
                    </p>}
                </div>
              ))}
            </div>
          : null}
        </div>
      </div>
    </div>
  );
}
