"use client";

import Link from "next/link";
import { Clock } from "lucide-react";
import { format, parseISO } from "date-fns";
import { ar } from "date-fns/locale";

import { EmptyState } from "@/components/shared/EmptyState";
import { Badge } from "@/components/ui/badge";
import { eventDotClass } from "@/lib/calendar/event-colors";
import { calendarEventTypeLabel } from "@/lib/calendar/event-labels";
import { formatTime } from "@/lib/utils/format-date";
import type { CalendarAgendaDayDto, CalendarEventDto } from "@/types/calendar";

type Props = {
  days: CalendarAgendaDayDto[];
  loading?: boolean;
  onSelectEvent: (event: CalendarEventDto) => void;
};

export function CalendarAgendaList({ days, loading, onSelectEvent }: Props) {
  if (loading) {
    return (
      <div className="flex min-h-[16rem] items-center justify-center text-sm text-[var(--color-muted-foreground)]">
        جارٍ تحميل الأجندة…
      </div>
    );
  }

  if (days.length === 0) {
    return (
      <EmptyState
        title="لا مواعيد في النطاق"
        description="غيّر الفلاتر أو أضف موعداً جديداً."
      />
    );
  }

  return (
    <div className="divide-y divide-[var(--color-border)]">
      {days.map((day) => {
        const label = format(parseISO(day.date), "EEEE، d MMMM yyyy", {
          locale: ar,
        });
        return (
          <section key={day.date}>
            <h3 className="bg-[var(--color-surface-muted)]/60 px-4 py-2 text-sm font-semibold text-[var(--color-navy-800)]">
              {label}
            </h3>
            {day.events.length === 0 ?
              <p className="px-4 py-3 text-sm text-[var(--color-muted-foreground)]">
                لا مواعيد
              </p>
            : <ul>
                {day.events.map((ev) => (
                  <li key={ev.id}>
                    <button
                      type="button"
                      className="flex w-full items-start gap-3 px-4 py-3 text-start transition-colors hover:bg-[var(--color-navy-50)]/40"
                      onClick={() => onSelectEvent(ev)}
                    >
                      <div className="flex min-w-[3.5rem] flex-col items-center rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-2 py-1.5">
                        <Clock className="size-3.5 text-[var(--color-navy-600)]" />
                        <span className="font-mono text-xs font-bold ltr-nums">
                          {ev.allDay ? "اليوم" : formatTime(ev.startAt)}
                        </span>
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge className={eventDotClass(ev)}>
                            {calendarEventTypeLabel(ev)}
                          </Badge>
                          <span className="truncate text-sm font-semibold">
                            {ev.title}
                          </span>
                        </div>
                        {ev.clientName && ev.sourceModule === "DELEGATION" ?
                          <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
                            الموكل: {ev.clientName}
                          </p>
                        : ev.caseNumber ?
                          <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
                            #{ev.caseNumber}
                            {ev.caseTitle ? ` — ${ev.caseTitle}` : ""}
                          </p>
                        : null}
                      </div>
                      {ev.sourceHref && !ev.editable ?
                        <Link
                          href={ev.sourceHref}
                          className="shrink-0 text-xs text-[var(--color-navy-600)] hover:underline"
                          onClick={(e) => e.stopPropagation()}
                        >
                          المصدر
                        </Link>
                      : null}
                    </button>
                  </li>
                ))}
              </ul>
            }
          </section>
        );
      })}
    </div>
  );
}
