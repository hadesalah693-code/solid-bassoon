"use client";

import Link from "next/link";
import { ArrowLeft, CalendarDays } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { eventDotClass } from "@/lib/calendar/event-colors";
import { CALENDAR_EVENT_TYPE_AR } from "@/lib/calendar/event-labels";
import { formatTime } from "@/lib/utils/format-date";
import type { CalendarEventDto } from "@/types/calendar";

type Props = {
  events: CalendarEventDto[];
  reportedTodayCount?: number;
  loading?: boolean;
};

export function DashboardCalendarToday({
  events,
  reportedTodayCount,
  loading,
}: Props) {
  const mismatch =
    (reportedTodayCount ?? 0) > events.length && events.length >= 0;

  return (
    <SectionCard
      className="shadow-[var(--shadow-xs)]"
      title="مواعيد اليوم"
      description="من التقويم المركزي."
      action={
        <Button variant="ghost" size="sm" asChild>
          <Link href="/calendar" className="inline-flex items-center gap-1">
            التقويم
            <ArrowLeft className="size-3.5 opacity-60" aria-hidden />
          </Link>
        </Button>
      }
      flush
    >
      {loading ?
        <div className="flex min-h-[8rem] items-center justify-center text-sm text-[var(--color-muted-foreground)]">
          جارٍ التحميل…
        </div>
      : events.length === 0 ?
        <div className="p-5">
          <EmptyState
            title="لا مواعيد اليوم"
            description={
              mismatch ?
                `الملخص يظهر ${reportedTodayCount} موعداً؛ قد لا تشملها صلاحيات العرض.`
              : "لا مواعيد مجدولة لليوم."
            }
            icon={CalendarDays}
          />
        </div>
      : <ul className="divide-y divide-[var(--color-border-subtle)]">
          {events.slice(0, 8).map((ev) => (
            <li key={ev.id} className="flex gap-3.5 px-5 py-3 transition-colors hover:bg-[var(--color-surface-muted)]/50">
              <div className="flex min-w-[3.25rem] flex-col items-center justify-center rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-1.5 py-1">
                <span className="text-[0.625rem] font-bold leading-none text-[var(--color-muted-foreground)]">
                  {ev.allDay ? "—" : ""}
                </span>
                <span className="font-mono text-sm font-bold leading-none text-[var(--color-navy-800)] ltr-nums">
                  {ev.allDay ? "—" : formatTime(ev.startAt)}
                </span>
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge className={eventDotClass(ev)}>
                    {CALENDAR_EVENT_TYPE_AR[ev.eventType]}
                  </Badge>
                  {ev.sourceHref ?
                    <Link
                      href={ev.sourceHref}
                      className="truncate text-[0.8125rem] font-bold text-[var(--color-foreground)] underline-offset-2 hover:underline"
                    >
                      {ev.title}
                    </Link>
                  : <span className="truncate text-[0.8125rem] font-bold">{ev.title}</span>}
                </div>
                {ev.caseNumber ?
                  <p className="mt-0.5 text-[0.6875rem] text-[var(--color-muted-foreground)]">
                    <span className="font-mono ltr-nums">#{ev.caseNumber}</span>
                    {ev.caseTitle ? ` — ${ev.caseTitle}` : ""}
                  </p>
                : null}
              </div>
            </li>
          ))}
        </ul>
      }
    </SectionCard>
  );
}
