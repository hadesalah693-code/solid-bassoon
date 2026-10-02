"use client";

import Link from "next/link";
import { ChevronLeft, Scale } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { CaseResponseDto, DisputePathTypeDto } from "@/types/api";

/** Short Arabic labels aligned with مسار القضية (avoid importing create form module). */
const DISPUTE_PATH_LABEL: Record<DisputePathTypeDto, string> = {
    CIVIL: "مسار مدني",
    COMMERCIAL: "تجاري",
    LABOR: "عمالي",
    CRIMINAL: "جنائي",
    ADMINISTRATIVE: "إداري",
    ARBITRATION: "تحكيم",
    CUSTOM: "مخصص",
  };

type DashboardCaseSnapshotProps = {
  activeCases: number;
  cases: CaseResponseDto[];
};

export function DashboardCaseSnapshot({
  activeCases,
  cases,
}: DashboardCaseSnapshotProps) {
  function pathLabel(c: CaseResponseDto): string | null {
    if (!c.disputePathType) return null;
    return DISPUTE_PATH_LABEL[c.disputePathType] ?? null;
  }

  return (
    <SectionCard
      className="border-[var(--color-border-strong)]/25 shadow-[var(--shadow-soft)]"
      title="ملخص القضايا"
      description="عدد القضايا النشطة وآخر الملفات المحدّثة مع اختصار لمسار النزاع."
      action={
        <Button variant="ghost" size="sm" asChild>
          <Link href="/cases">كل القضايا</Link>
        </Button>
      }
      flush
    >
      <div className="border-b border-[var(--color-border)]/80 bg-gradient-to-l from-[var(--color-navy-50)]/90 to-transparent px-6 py-5">
        <p className="text-xs font-medium text-[var(--color-muted-foreground)]">
          قضايا نشطة
        </p>
        <p className="mt-1 flex items-baseline gap-2">
          <span className="text-3xl font-bold tabular-nums tracking-tight text-[var(--color-foreground)] ltr-nums">
            {activeCases}
          </span>
          <span className="text-xs text-[var(--color-muted-foreground)]">
            من ملخص لوحة العمليات
          </span>
        </p>
      </div>

      {cases.length === 0 ? (
        <div className="p-6">
          <EmptyState
            title="لا قضايا في المعاينة السريعة"
            description="ستظهر هنا آخر القضايا المحدَّثة. أنشئ قضية جديدة أو افتح قائمة القضايا."
            icon={Scale}
          />
        </div>
      ) : (
        <ul className="divide-y divide-[var(--color-border)]/90">
          {cases.map((c) => {
            const path = pathLabel(c);
            return (
              <li
                key={c.id}
                className="flex flex-col gap-3 px-5 py-4 transition-colors hover:bg-[var(--color-surface-muted)]/45 sm:flex-row sm:items-center sm:justify-between sm:gap-4"
              >
                <div className="min-w-0 flex-1">
                  <Link
                    href={`/cases/${c.id}`}
                    className="block truncate text-sm font-semibold text-[var(--color-foreground)] underline-offset-2 hover:underline"
                  >
                    {c.title}
                  </Link>
                  <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-[var(--color-muted-foreground)]">
                    <span className="font-mono ltr-nums">#{c.caseNumber}</span>
                    {path ? (
                      <>
                        <span aria-hidden>·</span>
                        <Badge
                          tone="navy"
                          className="rounded-md px-1.5 py-0 text-[10px]"
                        >
                          {path}
                        </Badge>
                      </>
                    ) : null}
                  </div>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  asChild
                  className="shrink-0 gap-1"
                >
                  <Link href={`/cases/${c.id}?section=disputePath`}>
                    مسار النزاع
                    <ChevronLeft
                      className="size-3.5 text-[var(--color-muted-foreground)]"
                      aria-hidden
                    />
                  </Link>
                </Button>
              </li>
            );
          })}
        </ul>
      )}
    </SectionCard>
  );
}
