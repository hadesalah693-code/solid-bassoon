"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeftRight, CalendarClock } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { mandatesApi } from "@/lib/api/mandates";
import { MANDATE_STATUS_AR, MANDATE_STATUS_TONE } from "@/lib/mandates/labels";
import { formatDateTime } from "@/lib/utils/format-date";
import type { MandateListItemDto } from "@/types/mandate";

export function MandateUpcomingWidget() {
  const [rows, setRows] = useState<MandateListItemDto[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await mandatesApi.list({
          size: 5,
          sessionFrom: new Date(Date.now()).toISOString(),
        });
        if (!cancelled) {
          const now = Date.now();
          setRows(
            res.content.filter(
              (r) =>
                r.status !== "COMPLETED" &&
                r.status !== "CANCELLED" &&
                r.sessionDate &&
                new Date(r.sessionDate).getTime() >= now,
            ),
          );
        }
      } catch {
        if (!cancelled) setRows([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <SectionCard
      title="إنابات قادمة"
      description="جلسات الإنابات في الفترة القادمة"
      action={
        <Link
          href="/mandates"
          className="inline-flex items-center gap-1 text-[0.6875rem] font-bold text-[var(--color-gold-600)] hover:underline"
        >
          عرض الكل
          <ArrowLeftRight className="size-3" aria-hidden />
        </Link>
      }
    >
      {loading ? (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="h-14 animate-pulse rounded-xl bg-[var(--color-surface-muted)]"
              aria-hidden
            />
          ))}
        </div>
      ) : rows.length === 0 ? (
        <p className="flex items-center gap-2 py-3 text-sm text-[var(--color-muted-foreground)]">
          <CalendarClock className="size-4" aria-hidden />
          لا توجد إنابات قادمة.
        </p>
      ) : (
        <ul className="divide-y divide-[var(--color-border-subtle)]">
          {rows.map((r) => (
            <li key={r.id}>
              <Link
                href={`/mandates/${r.id}`}
                className="block px-2 py-3 transition-colors hover:bg-[var(--color-surface-muted)]/50 rounded-lg"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-bold ltr-nums text-[var(--color-gold-600)] text-[0.8125rem]">
                    {r.mandateNumber}
                  </span>
                  <Badge tone={MANDATE_STATUS_TONE[r.status]}>
                    {MANDATE_STATUS_AR[r.status]}
                  </Badge>
                </div>
                <p className="mt-1 truncate text-[0.8125rem] ltr-nums">{r.caseNumber}</p>
                <p className="mt-0.5 text-[0.6875rem] text-[var(--color-muted-foreground)]">
                  {r.sessionDate ? formatDateTime(r.sessionDate) : "—"}
                  {r.substitutedLawyer?.fullName
                    ? ` · ${r.substitutedLawyer.fullName}`
                    : ""}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}
