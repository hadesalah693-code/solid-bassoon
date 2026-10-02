"use client";

import { Search } from "lucide-react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  CALENDAR_EVENT_TYPE_AR,
  CALENDAR_STATUS_AR,
} from "@/lib/calendar/event-labels";
import { cn } from "@/lib/utils/cn";
import type { CalendarEventStatus, CalendarEventType } from "@/types/calendar";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const EVENT_TYPES: (CalendarEventType | "ALL")[] = [
  "ALL",
  "HEARING",
  "TASK",
  "MEETING",
  "CALL",
  "REMINDER",
  "DOCUMENT_EXPIRY",
  "DEADLINE",
  "CUSTOM",
];

const STATUSES: (CalendarEventStatus | "ALL")[] = [
  "ALL",
  "SCHEDULED",
  "COMPLETED",
  "CANCELLED",
];

type Props = {
  eventType: CalendarEventType | "ALL";
  status: CalendarEventStatus | "ALL";
  search: string;
  onEventTypeChange: (v: CalendarEventType | "ALL") => void;
  onStatusChange: (v: CalendarEventStatus | "ALL") => void;
  onSearchChange: (v: string) => void;
};

export function CalendarFilters({
  eventType,
  status,
  search,
  onEventTypeChange,
  onStatusChange,
  onSearchChange,
}: Props) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div>
        <Label htmlFor="cal-filter-type">النوع</Label>
        <select
          id="cal-filter-type"
          className={cn(selectClassName, "mt-1.5")}
          value={eventType}
          onChange={(e) =>
            onEventTypeChange(e.target.value as CalendarEventType | "ALL")
          }
        >
          {EVENT_TYPES.map((t) => (
            <option key={t} value={t}>
              {t === "ALL" ? "الكل" : t === "DOCUMENT_EXPIRY" ? "انتهاء توكيل / مستند" : CALENDAR_EVENT_TYPE_AR[t]}
            </option>
          ))}
        </select>
      </div>
      <div>
        <Label htmlFor="cal-filter-status">الحالة</Label>
        <select
          id="cal-filter-status"
          className={cn(selectClassName, "mt-1.5")}
          value={status}
          onChange={(e) =>
            onStatusChange(e.target.value as CalendarEventStatus | "ALL")
          }
        >
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s === "ALL" ? "الكل" : CALENDAR_STATUS_AR[s]}
            </option>
          ))}
        </select>
      </div>
      <div className="sm:col-span-2 lg:col-span-2">
        <Label htmlFor="cal-filter-search">بحث</Label>
        <div className="relative mt-1.5">
          <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-muted-foreground)]" />
          <Input
            id="cal-filter-search"
            className="ps-9"
            placeholder="عنوان، قضية، عميل…"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>
      </div>
    </div>
  );
}
