"use client";

import Link from "next/link";
import { ArrowLeft, Clock, MapPin } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Badge } from "@/components/ui/badge";
import { HearingStatusBadge } from "@/components/shared/StatusBadge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";
import { formatTime } from "@/lib/utils/format-date";
import type { Hearing } from "@/types/hearing";

type DashboardHearingsOpsProps = {
  hearings: Hearing[];
  /** When preview list doesn't cover all hearings counted in KPI */
  reportedTodayCount?: number;
  description?: string;
};

function lawyerLine(h: Hearing): string | null {
  const n = h.assignedLawyer?.fullName?.trim();
  return n || null;
}

export function DashboardHearingsOps({
  hearings,
  reportedTodayCount,
  description,
}: DashboardHearingsOpsProps) {
  const mismatch =
    (reportedTodayCount ?? 0) > hearings.length &&
    hearings.length >= 0;

  const desc =
    description ??
    "جدول تشغيلي لجلسات اليوم المرصودة في العيّنة السريعة — للتفاصيل الكاملة استخدم قائمة الجلسات.";

  return (
    <SectionCard
      className="border-[var(--color-border-strong)]/25 shadow-[var(--shadow-soft)] ring-1 ring-[var(--color-border)]/35"
      title="جدول الجلسات اليوم"
      description={desc}
      action={
        <Button variant="ghost" size="sm" asChild>
          <Link href="/hearings" className="inline-flex items-center gap-1">
            كل الجلسات
            <ArrowLeft className="size-3.5 opacity-70" aria-hidden />
          </Link>
        </Button>
      }
      flush
    >
      {hearings.length === 0 ?
        <div className="p-6">
          <EmptyState
            title="لا مواعيد في المعاينة"
            description={
              mismatch ?
                `الخلاصة تشير إلى ${reportedTodayCount} موعد اليوم؛ المعاينة قد لا تشمل كل الملفات.`
              : "لم تُحمّل جلسات اليوم ضمن هذا الجزء؛ راجع صفحة الجلسات."
            }
          />
        </div>
      :
        <ul className="divide-y divide-[var(--color-border)]/90">
          {hearings.map((h) => {
            const lawyer = lawyerLine(h);
            return (
              <li key={h.id} className="flex gap-4 px-5 py-3.5 hover:bg-[var(--color-navy-50)]/35">
                <div className="flex min-w-[3.75rem] flex-col items-center justify-center rounded-xl border border-[var(--color-border)]/80 bg-[var(--color-surface)] px-2 py-1.5 shadow-sm">
                  <Clock className="size-4 text-[var(--color-navy-600)]" aria-hidden />
                  <span className="font-mono text-sm font-bold leading-none text-[var(--color-navy-800)] ltr-nums">
                    {formatTime(h.scheduledAt)}
                  </span>
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <HearingStatusBadge value={h.status} />
                    <Link
                      href={`/cases/${h.caseId}`}
                      className="truncate text-sm font-bold text-[var(--color-foreground)] underline-offset-2 hover:underline"
                    >
                      {h.caseTitle}
                    </Link>
                    <span className="font-mono text-[11px] text-[var(--color-muted-foreground)] ltr-nums">
                      #{h.caseNumber}
                    </span>
                  </div>
                  <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px] text-[var(--color-muted-foreground)]">
                    {h.court ?
                      <span className="flex min-w-0 items-center gap-1">
                        <MapPin className="size-3.5 shrink-0" aria-hidden />
                        <span className="truncate">{h.court}{h.room ? ` — ${h.room}` : ""}</span>
                      </span>
                    : null}
                    {lawyer ?
                      <Badge tone="navy" className="text-[10px]">
                        المحامي: {lawyer}
                      </Badge>
                    : null}
                  </div>
                  <div className="mt-2">
                    <Button variant="outline" size="sm" className="h-8 gap-1 text-xs" asChild>
                      <Link href={`/cases/${h.caseId}?section=hearings`}>سجل القضية</Link>
                    </Button>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      }
      {hearings.length > 0 && mismatch ?
        <p
          className={cn(
            "border-t border-[var(--color-border)]/80 bg-[var(--color-gold-50)]/30 px-5 py-2 text-[11px] text-[var(--color-muted-foreground)]",
          )}
        >
          قد لا تعرض هذه القائمة كل جلسات اليوم المُشار إليها في المؤشر الإحصائي.
        </p>
      : null}
    </SectionCard>
  );
}
