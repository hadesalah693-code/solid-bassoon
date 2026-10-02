import { format, parseISO } from "date-fns";
import type { EventInput } from "@fullcalendar/core";

import { getEventColor } from "@/lib/calendar/event-colors";
import type { CalendarEventDto } from "@/types/calendar";

/** API LocalDateTime → `datetime-local` value. */
export function apiDateTimeToLocalInput(value: string | null | undefined): string {
  if (!value) return "";
  const t = value.trim();
  return t.length >= 16 ? t.slice(0, 16) : t;
}

/** Date → Jackson LocalDateTime string. */
export function dateToApiDateTime(d: Date): string {
  return format(d, "yyyy-MM-dd'T'HH:mm:ss");
}

/** Date → Jackson LocalDate string. */
export function dateToApiDate(d: Date): string {
  return format(d, "yyyy-MM-dd");
}

export function calendarEventToFc(event: CalendarEventDto): EventInput {
  const colors = getEventColor(event.colorKey);
  return {
    id: event.id,
    title: event.title,
    start: event.startAt,
    end: event.endAt ?? undefined,
    allDay: event.allDay,
    extendedProps: { kalimaEvent: event },
    backgroundColor: colors.bg,
    borderColor: colors.border,
    textColor: colors.text,
  };
}

export function sortEventsByStart(events: CalendarEventDto[]): CalendarEventDto[] {
  return [...events].sort((a, b) => {
    const sa = parseISO(a.startAt).getTime();
    const sb = parseISO(b.startAt).getTime();
    return sa - sb;
  });
}
