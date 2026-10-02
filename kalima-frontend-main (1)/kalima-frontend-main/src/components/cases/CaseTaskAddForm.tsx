"use client";

import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";
import { staffApi } from "@/lib/api/staff";
import { tasksApi } from "@/lib/api/tasks";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { refreshMyOpenTasks } from "@/lib/hooks/use-my-open-tasks";
import { localInputToApiDateTime } from "@/lib/utils/datetime-api";
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
const STATUS: TaskResponseDto["status"][] = [
  "TODO",
  "IN_PROGRESS",
  "DONE",
  "CANCELLED",
];

const PRI_AR: Record<TaskResponseDto["priority"], string> = {
  LOW: "منخفض",
  MEDIUM: "متوسط",
  HIGH: "عالٍ",
  URGENT: "عاجل",
};

const ST_AR: Record<TaskResponseDto["status"], string> = {
  TODO: "قيد الانتظار",
  IN_PROGRESS: "قيد التنفيذ",
  DONE: "منجز",
  CANCELLED: "ملغاة",
};

type Props = {
  caseId: string;
  disabled?: boolean;
  onCreated: () => void;
};

export function CaseTaskAddForm({ caseId, disabled, onCreated }: Props) {
  const [staff, setStaff] = useState<Staff[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] =
    useState<TaskResponseDto["priority"]>("MEDIUM");
  const [status, setStatus] = useState<TaskResponseDto["status"]>("TODO");
  const [dueDate, setDueDate] = useState("");
  const [assignedToId, setAssignedToId] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let c = false;
    (async () => {
      try {
        const [a, b] = await Promise.all([
          staffApi.list({ page: 0, size: 200, role: "LAWYER" }),
          staffApi.list({ page: 0, size: 200, role: "SENIOR_LAWYER" }),
        ]);
        if (c) return;
        const by = new Map<string, Staff>();
        [...a.content, ...b.content].forEach((s) => {
          if (s.isActive) {
            by.set(s.id, s);
          }
        });
        setStaff(
          [...by.values()].sort((x, y) =>
            x.fullName.localeCompare(y.fullName, "ar"),
          ),
        );
      } catch {
        if (!c) setStaff([]);
      }
    })();
    return () => {
      c = true;
    };
  }, []);

  if (disabled) {
    return null;
  }

  async function submit() {
    const t = title.trim();
    if (!t) {
      notifyError("أدخل عنوان المهمة");
      return;
    }
    setSaving(true);
    try {
      const due = dueDate.trim() ? localInputToApiDateTime(dueDate) : null;
      await tasksApi.create(caseId, {
        title: t,
        description: description.trim() || null,
        priority,
        status,
        dueDate: due || null,
        assignedToId: assignedToId || null,
      });
      setTitle("");
      setDescription("");
      setDueDate("");
      setAssignedToId("");
      setPriority("MEDIUM");
      setStatus("TODO");
      notifySuccess("تمت إضافة المهمة");
      void refreshMyOpenTasks();
      onCreated();
    } catch (e) {
      notifyApiError(e, "تعذر إضافة المهمة");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-4">
      <p className="text-sm font-medium">مهمة جديدة</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor="t-title">العنوان *</Label>
          <Input
            id="t-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor="t-desc">وصف (اختياري)</Label>
          <Textarea
            id="t-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            className="resize-y"
            disabled={saving}
          />
        </div>
        <div className="space-y-1">
          <Label>الأولوية</Label>
          <select
            className={selectClassName}
            value={priority}
            onChange={(e) =>
              setPriority(e.target.value as TaskResponseDto["priority"])
            }
            disabled={saving}
          >
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {PRI_AR[p]}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label>الحالة</Label>
          <select
            className={selectClassName}
            value={status}
            onChange={(e) =>
              setStatus(e.target.value as TaskResponseDto["status"])
            }
            disabled={saving}
          >
            {STATUS.map((s) => (
              <option key={s} value={s}>
                {ST_AR[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="t-due">موعد (اختياري)</Label>
          <Input
            id="t-due"
            type="datetime-local"
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1">
          <Label>مكلَّف (اختياري)</Label>
          <select
            className={selectClassName}
            value={assignedToId}
            onChange={(e) => setAssignedToId(e.target.value)}
            disabled={saving}
          >
            <option value="">— بدون —</option>
            {staff.map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName}
              </option>
            ))}
          </select>
        </div>
      </div>
      <Button
        type="button"
        variant="gold"
        size="sm"
        disabled={saving}
        onClick={() => void submit()}
      >
        {saving ? "…" : "إضافة المهمة"}
      </Button>
    </div>
  );
}
