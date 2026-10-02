import { Clock, MapPin } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { HearingStatusBadge } from "@/components/shared/StatusBadge";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { formatTime } from "@/lib/utils/format-date";
import type { Hearing } from "@/types/hearing";
import Link from "next/link";

export function TodayHearings({
  hearings,
  description = "جلسات اليوم بحسب تقويم المكتب، مع موعد وقضية لكل موعد.",
  serverHearingsTodayCount,
}: {
  hearings: Hearing[];
  description?: string;
  /** When > 0 but preview list is empty, explains mismatch with summary count */
  serverHearingsTodayCount?: number;
}) {
  const noPreviewButServerHas =
    (serverHearingsTodayCount ?? 0) > 0 && hearings.length === 0;
  return (
    <SectionCard
      className="border-[var(--color-border-strong)]/25 shadow-[var(--shadow-soft)]"
      title="جلسات قريبة"
      description={description}
      action={
        <Button variant="ghost" size="sm" asChild>
          <Link href="/hearings">الجلسات</Link>
        </Button>
      }
      flush
    >
      {hearings.length === 0 ? (
        <div className="p-6">
          <EmptyState
            title={
              noPreviewButServerHas
                ? "لا جلسات في المعاينة"
                : "لا توجد جلسات في المعاينة"
            }
            description={
              noPreviewButServerHas
                ? "الملخص يظهر أن لديك جلسات اليوم، لكن المعاينة السريعة لم تُحمّل صفوفاً — قد تكون خارج أول الدفعات المسترجعة."
                : "لا جلسات في نطاق المعاينة السريعة. راجع تقويم الجلسات للاطلاع الكامل."
            }
          />
        </div>
      ) : (
        <ul className="divide-y divide-[var(--color-border)]">
          {hearings.map((h) => (
            <li
              key={h.id}
              className="flex items-center gap-4 px-6 py-4 transition-colors hover:bg-[var(--color-surface-muted)]/50"
            >
              <div className="flex size-12 flex-col items-center justify-center rounded-xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)] ltr-nums">
                <Clock className="size-4" />
                <span className="mt-0.5 text-xs font-semibold">
                  {formatTime(h.scheduledAt)}
                </span>
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-[var(--color-foreground)]">
                  {h.caseTitle}
                </p>
                <p className="mt-0.5 flex items-center gap-2 text-xs text-[var(--color-muted-foreground)]">
                  <span className="ltr-nums">#{h.caseNumber}</span>
                  {h.court ? (
                    <>
                      <span>•</span>
                      <span className="flex items-center gap-1">
                        <MapPin className="size-3" />
                        {h.court}
                        {h.room ? ` — ${h.room}` : ""}
                      </span>
                    </>
                  ) : null}
                </p>
              </div>
              <HearingStatusBadge value={h.status} />
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}
