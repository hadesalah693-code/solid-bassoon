"use client";

import Link from "next/link";
import { ArrowLeft, ClipboardList } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";

type DashboardTasksTeaserProps = {
  pendingTasks: number;
};

export function DashboardTasksTeaser({ pendingTasks }: DashboardTasksTeaserProps) {
  return (
    <SectionCard
      className="border-[var(--color-border-strong)]/25 shadow-[var(--shadow-soft)]"
      title="مهام تحتاج متابعة"
      description="مهام غير منجزة حسب الموجز الإحصائي — التفاصيل في صفحة المهام."
      action={
        <Button variant="ghost" size="sm" asChild>
          <Link href="/tasks" className="inline-flex items-center gap-1">
            لوحة المهام
            <ArrowLeft className="size-3.5 opacity-70" aria-hidden />
          </Link>
        </Button>
      }
    >
      {pendingTasks <= 0 ? (
        <div className="rounded-xl border border-dashed border-[var(--color-border-strong)] bg-[var(--color-surface-muted)]/35 px-4 py-6">
          <EmptyState
            title="لا مهام متأخرة وفقاً للملخص"
            description="راجع قائمة المهام للاطلاع على التفاصيل والأولويات."
            icon={ClipboardList}
          />
        </div>
      ) : (
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-xs font-medium text-[var(--color-muted-foreground)]">
              مهام معلّقة
            </p>
            <p className="mt-1 flex items-baseline gap-2 tabular-nums">
              <span className="text-3xl font-bold text-[var(--color-foreground)] ltr-nums">
                {pendingTasks}
              </span>
              <span className="text-sm text-[var(--color-muted-foreground)]">
                من الملخص التشغيلي
              </span>
            </p>
          </div>
          <Button variant="gold" size="sm" asChild className="w-full shrink-0 sm:w-auto">
            <Link href="/tasks">عرض كل المهام</Link>
          </Button>
        </div>
      )}
    </SectionCard>
  );
}
