"use client";

import { useEffect, useState } from "react";
import { Loader2, Save, X } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { calendarApi } from "@/lib/api/calendar";
import { ApiRequestError } from "@/lib/api/client";
import { staffApi } from "@/lib/api/staff";
import {
  CALENDAR_EVENT_TYPE_AR,
  NATIVE_EVENT_TYPES,
  REMINDER_OFFSET_OPTIONS,
} from "@/lib/calendar/event-labels";
import {
  apiDateTimeToLocalInput,
  dateToApiDateTime,
} from "@/lib/calendar/calendar-utils";
import { cn } from "@/lib/utils/cn";
import { localInputToApiDateTime } from "@/lib/utils/datetime-api";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import type { CalendarEventDto, NativeCalendarEventType } from "@/types/calendar";
import type { Staff } from "@/types/staff";
import type { TaskResponseDto } from "@/types/api";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const PRIORITIES: TaskResponseDto["priority"][] = [
  "LOW",
  "MEDIUM",
  "HIGH",
  "URGENT",
];

const PRI_AR: Record<TaskResponseDto["priority"], string> = {
  LOW: "منخفض",
  MEDIUM: "متوسط",
  HIGH: "عالٍ",
  URGENT: "عاجل",
};

type Props = {
  open: boolean;
  mode: "create" | "edit";
  initial?: CalendarEventDto | null;
  defaultCaseId?: string | null;
  onClose: () => void;
  onSaved: (event: CalendarEventDto) => void;
};

export function CalendarEventForm({
  open,
  mode,
  initial,
  defaultCaseId,
  onClose,
  onSaved,
}: Props) {
  const [staff, setStaff] = useState<Staff[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [eventType, setEventType] = useState<NativeCalendarEventType>("MEETING");
  const [startAt, setStartAt] = useState("");
  const [endAt, setEndAt] = useState("");
  const [allDay, setAllDay] = useState(false);
  const [priority, setPriority] = useState<TaskResponseDto["priority"]>("MEDIUM");
  const [assignedToId, setAssignedToId] = useState("");
  const [caseId, setCaseId] = useState("");
  const [reminders, setReminders] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setError(null);
    if (mode === "edit" && initial) {
      setTitle(initial.title);
      setDescription(initial.description ?? "");
      setEventType(
        NATIVE_EVENT_TYPES.includes(initial.eventType as NativeCalendarEventType) ?
          (initial.eventType as NativeCalendarEventType)
        : "CUSTOM",
      );
      setStartAt(apiDateTimeToLocalInput(initial.startAt));
      setEndAt(apiDateTimeToLocalInput(initial.endAt));
      setAllDay(initial.allDay);
      setPriority(initial.priority ?? "MEDIUM");
      setAssignedToId(initial.assignedToId ?? "");
      setCaseId(initial.caseId ?? "");
      setReminders(initial.reminderOffsetsMinutes ?? []);
    } else {
      setTitle("");
      setDescription("");
      setEventType("MEETING");
      const now = new Date();
      now.setMinutes(0, 0, 0);
      now.setHours(now.getHours() + 1);
      setStartAt(apiDateTimeToLocalInput(dateToApiDateTime(now)));
      setEndAt("");
      setAllDay(false);
      setPriority("MEDIUM");
      setAssignedToId("");
      setCaseId(defaultCaseId ?? "");
      setReminders([]);
    }
  }, [open, mode, initial, defaultCaseId]);

  useEffect(() => {
    if (!open) return;
    let c = false;
    (async () => {
      try {
        const page = await staffApi.list({ page: 0, size: 100 });
        if (!c) setStaff(page.content);
      } catch {
        if (!c) setStaff([]);
      }
    })();
    return () => {
      c = true;
    };
  }, [open]);

  function toggleReminder(minutes: number) {
    setReminders((prev) =>
      prev.includes(minutes) ?
        prev.filter((m) => m !== minutes)
      : [...prev, minutes].sort((a, b) => a - b),
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) {
      notifyError("أدخل عنوان الموعد");
      return;
    }
    if (!startAt.trim()) {
      notifyError("حدد وقت البداية");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const body = {
        title: title.trim(),
        description: description.trim() || undefined,
        eventType,
        caseId: caseId.trim() || null,
        startAt: localInputToApiDateTime(startAt),
        endAt: endAt.trim() ? localInputToApiDateTime(endAt) : null,
        allDay,
        priority,
        assignedToId: assignedToId.trim() || null,
        reminderOffsetsMinutes: reminders.length ? reminders : undefined,
      };
      const saved =
        mode === "edit" && initial ?
          await calendarApi.patchEvent(initial.id, body)
        : await calendarApi.createEvent(body);
      notifySuccess(mode === "edit" ? "تم تحديث الموعد" : "تم إنشاء الموعد");
      onSaved(saved);
      onClose();
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.details?.message ?? err.message);
      } else {
        notifyApiError(err, "تعذر حفظ الموعد");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <AnimatePresence>
      {open ?
        <>
          <motion.button
            type="button"
            className="fixed inset-0 z-40 bg-black/40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            aria-label="إغلاق"
            onClick={onClose}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            className="fixed inset-x-4 top-[6vh] z-50 mx-auto max-h-[88vh] max-w-xl overflow-y-auto rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-xl sm:inset-x-auto sm:start-1/2 sm:-translate-x-1/2 rtl:sm:translate-x-1/2"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 16 }}
          >
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-lg font-bold">
                {mode === "edit" ? "تعديل موعد" : "موعد جديد"}
              </h2>
              <Button variant="ghost" size="icon" onClick={onClose} aria-label="إغلاق">
                <X className="size-4" />
              </Button>
            </div>

            {error ?
              <div className="mt-3">
                <ApiErrorAlert message={error} title="تعذر الحفظ" />
              </div>
            : null}

            <form onSubmit={handleSubmit} className="mt-4 space-y-4">
              <div>
                <Label htmlFor="cal-title">العنوان</Label>
                <Input
                  id="cal-title"
                  className="mt-1.5"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
              </div>
              <div>
                <Label htmlFor="cal-type">النوع</Label>
                <select
                  id="cal-type"
                  className={cn(selectClassName, "mt-1.5")}
                  value={eventType}
                  onChange={(e) =>
                    setEventType(e.target.value as NativeCalendarEventType)
                  }
                >
                  {NATIVE_EVENT_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {CALENDAR_EVENT_TYPE_AR[t]}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <Label htmlFor="cal-desc">الوصف</Label>
                <Textarea
                  id="cal-desc"
                  className="mt-1.5"
                  rows={3}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <Label htmlFor="cal-start">البداية</Label>
                  <Input
                    id="cal-start"
                    type="datetime-local"
                    className="mt-1.5 ltr-nums"
                    value={startAt}
                    onChange={(e) => setStartAt(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <Label htmlFor="cal-end">النهاية (اختياري)</Label>
                  <Input
                    id="cal-end"
                    type="datetime-local"
                    className="mt-1.5 ltr-nums"
                    value={endAt}
                    onChange={(e) => setEndAt(e.target.value)}
                  />
                </div>
              </div>
              <label className="flex cursor-pointer items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={allDay}
                  onChange={(e) => setAllDay(e.target.checked)}
                />
                طوال اليوم
              </label>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <Label htmlFor="cal-priority">الأولوية</Label>
                  <select
                    id="cal-priority"
                    className={cn(selectClassName, "mt-1.5")}
                    value={priority}
                    onChange={(e) =>
                      setPriority(e.target.value as TaskResponseDto["priority"])
                    }
                  >
                    {PRIORITIES.map((p) => (
                      <option key={p} value={p}>
                        {PRI_AR[p]}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <Label htmlFor="cal-assignee">المُسند إليه</Label>
                  <select
                    id="cal-assignee"
                    className={cn(selectClassName, "mt-1.5")}
                    value={assignedToId}
                    onChange={(e) => setAssignedToId(e.target.value)}
                  >
                    <option value="">—</option>
                    {staff.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.fullName}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              {!defaultCaseId ?
                <div>
                  <Label htmlFor="cal-case">معرّف القضية (اختياري)</Label>
                  <Input
                    id="cal-case"
                    className="mt-1.5 font-mono text-xs ltr-nums"
                    value={caseId}
                    onChange={(e) => setCaseId(e.target.value)}
                    placeholder="UUID"
                  />
                </div>
              : null}
              <div>
                <Label>تذكيرات</Label>
                <div className="mt-2 flex flex-wrap gap-2">
                  {REMINDER_OFFSET_OPTIONS.map((opt) => {
                    const active = reminders.includes(opt.minutes);
                    return (
                      <button
                        key={opt.minutes}
                        type="button"
                        className={cn(
                          "rounded-full border px-3 py-1 text-xs transition-colors",
                          active ?
                            "border-[var(--color-navy-600)] bg-[var(--color-navy-50)] text-[var(--color-navy-800)]"
                          : "border-[var(--color-border)] text-[var(--color-muted-foreground)]",
                        )}
                        onClick={() => toggleReminder(opt.minutes)}
                      >
                        {opt.label}
                      </button>
                    );
                  })}
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="outline" onClick={onClose}>
                  إلغاء
                </Button>
                <Button type="submit" disabled={saving}>
                  {saving ?
                    <Loader2 className="size-4 animate-spin" />
                  : <Save className="size-4" />}
                  حفظ
                </Button>
              </div>
            </form>
          </motion.div>
        </>
      : null}
    </AnimatePresence>
  );
}
