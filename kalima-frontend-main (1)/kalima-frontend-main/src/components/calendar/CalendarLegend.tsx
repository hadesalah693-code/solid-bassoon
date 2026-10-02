"use client";

import { getEventColor } from "@/lib/calendar/event-colors";
import { CALENDAR_EVENT_TYPE_AR } from "@/lib/calendar/event-labels";
import type { CalendarEventType } from "@/types/calendar";

const LEGEND_TYPES: { type: CalendarEventType; colorKey: string; label?: string }[] = [
  { type: "HEARING", colorKey: "hearing" },
  { type: "TASK", colorKey: "task" },
  { type: "MEETING", colorKey: "meeting" },
  { type: "CALL", colorKey: "meeting" },
  { type: "REMINDER", colorKey: "reminder" },
  { type: "DOCUMENT_EXPIRY", colorKey: "delegation", label: "انتهاء توكيل" },
  { type: "DEADLINE", colorKey: "deadline" },
  { type: "CUSTOM", colorKey: "custom" },
];

export function CalendarLegend() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-[var(--color-muted-foreground)]">
      {LEGEND_TYPES.map((item) => {
        const c = getEventColor(item.colorKey);
        return (
          <span key={item.type} className="inline-flex items-center gap-1.5">
            <span
              className="size-2.5 rounded-full"
              style={{ backgroundColor: c.border }}
              aria-hidden
            />
            {item.label ?? CALENDAR_EVENT_TYPE_AR[item.type]}
          </span>
        );
      })}
    </div>
  );
}
