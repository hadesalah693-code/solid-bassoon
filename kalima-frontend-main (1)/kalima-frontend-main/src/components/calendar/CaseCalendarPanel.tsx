"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { CalendarDays, ExternalLink } from "lucide-react";

import { CalendarAgendaList } from "@/components/calendar/CalendarAgendaList";
import { CalendarEventDetail } from "@/components/calendar/CalendarEventDetail";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { Button } from "@/components/ui/button";
import { calendarApi } from "@/lib/api/calendar";
import { ApiRequestError } from "@/lib/api/client";
import { sortEventsByStart } from "@/lib/calendar/calendar-utils";
import type { CalendarEventDto } from "@/types/calendar";

type Props = {
  caseId: string;
  archived?: boolean;
};

export function CaseCalendarPanel({ caseId, archived }: Props) {
  const [events, setEvents] = useState<CalendarEventDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<CalendarEventDto | null>(null);
  const [loadKey, setLoadKey] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await calendarApi.forCase(caseId);
      setEvents(sortEventsByStart(list));
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setError(e.details?.message ?? e.message);
      } else {
        setError("تعذر تحميل مواعيد القضية");
      }
      setEvents([]);
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    void load();
  }, [load, loadKey]);

  const agendaDays = (() => {
    const map = new Map<string, CalendarEventDto[]>();
    for (const ev of events) {
      const day = ev.startAt.slice(0, 10);
      const arr = map.get(day) ?? [];
      arr.push(ev);
      map.set(day, arr);
    }
    return [...map.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, dayEvents]) => ({
        date,
        events: sortEventsByStart(dayEvents),
      }));
  })();

  if (loading) {
    return <PageLoading label="جارٍ تحميل التقويم…" minHeightClassName="min-h-[12rem]" />;
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-[var(--color-muted-foreground)]">
          مواعيد القضية من التقويم المركزي — الجلسات والمهام والمواعيد المرتبطة.
        </p>
        <Button variant="outline" size="sm" asChild>
          <Link
            href={`/calendar?caseId=${encodeURIComponent(caseId)}`}
            className="inline-flex items-center gap-1"
          >
            <CalendarDays className="size-4" />
            فتح في التقويم
            <ExternalLink className="size-3.5 opacity-70" />
          </Link>
        </Button>
      </div>

      {error ?
        <ApiErrorAlert
          message={error}
          title="تعذر التحميل"
          onRetry={() => setLoadKey((k) => k + 1)}
        />
      : null}

      {!error && events.length === 0 ?
        <EmptyState
          title="لا مواعيد مسجّلة"
          description={
            archived ?
              "لا توجد مواعيد مرتبطة بهذه القضية."
            : "أضف جلسة أو مهمة — ستظهر هنا تلقائياً."
          }
          icon={CalendarDays}
        />
      : null}

      {!error && events.length > 0 ?
        <div className="overflow-hidden rounded-xl border border-[var(--color-border)]">
          <CalendarAgendaList
            days={agendaDays}
            onSelectEvent={setSelected}
          />
        </div>
      : null}

      <CalendarEventDetail
        event={selected}
        open={Boolean(selected)}
        canEdit={false}
        onClose={() => setSelected(null)}
      />
    </div>
  );
}
