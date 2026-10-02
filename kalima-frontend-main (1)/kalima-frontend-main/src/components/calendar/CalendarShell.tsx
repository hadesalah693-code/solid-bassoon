"use client";

import { useCallback, useEffect, useMemo, useRef } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import arLocale from "@fullcalendar/core/locales/ar";
import type { CalendarApi, DatesSetArg, EventClickArg } from "@fullcalendar/core";

import { calendarEventToFc } from "@/lib/calendar/calendar-utils";
import type { CalendarEventDto, CalendarViewMode } from "@/types/calendar";

type Props = {
  events: CalendarEventDto[];
  viewMode: Exclude<CalendarViewMode, "agenda">;
  onDatesChange: (from: Date, to: Date) => void;
  onEventClick: (event: CalendarEventDto) => void;
  onDateClick?: (date: Date) => void;
};

export function CalendarShell({
  events,
  viewMode,
  onDatesChange,
  onEventClick,
  onDateClick,
}: Props) {
  const calendarRef = useRef<CalendarApi | null>(null);

  const fcView =
    viewMode === "month" ? "dayGridMonth"
    : viewMode === "week" ? "timeGridWeek"
    : "timeGridDay";

  const fcEvents = useMemo(() => events.map(calendarEventToFc), [events]);

  useEffect(() => {
    calendarRef.current?.changeView(fcView);
  }, [fcView]);

  const handleDatesSet = useCallback(
    (arg: DatesSetArg) => {
      onDatesChange(arg.start, arg.end);
    },
    [onDatesChange],
  );

  const handleEventClick = useCallback(
    (arg: EventClickArg) => {
      const ev = arg.event.extendedProps.kalimaEvent as CalendarEventDto | undefined;
      if (ev) onEventClick(ev);
    },
    [onEventClick],
  );

  return (
    <div className="calendar-shell rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-2 sm:p-3 [&_.fc]:text-sm [&_.fc-toolbar-title]:text-base [&_.fc-toolbar-title]:font-bold">
      <FullCalendar
        ref={(el) => {
          calendarRef.current = el?.getApi() ?? null;
        }}
        plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
        locale={arLocale}
        direction="rtl"
        initialView={fcView}
        headerToolbar={{
          start: "prev,next today",
          center: "title",
          end: "",
        }}
        height="auto"
        events={fcEvents}
        datesSet={handleDatesSet}
        eventClick={handleEventClick}
        dateClick={onDateClick ? (info) => onDateClick(info.date) : undefined}
        selectable={Boolean(onDateClick)}
        nowIndicator
        slotMinTime="06:00:00"
        slotMaxTime="23:00:00"
        allDayText="طوال اليوم"
        buttonText={{
          today: "اليوم",
        }}
      />
    </div>
  );
}
