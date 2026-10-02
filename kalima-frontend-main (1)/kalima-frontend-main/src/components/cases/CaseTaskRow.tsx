"use client";

import { useEffect, useState } from "react";
import { Loader2, Pencil } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { TableCell, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { CasePriorityBadge } from "@/components/shared/StatusBadge";
import { cn } from "@/lib/utils/cn";
import { staffApi } from "@/lib/api/staff";
import { tasksApi } from "@/lib/api/tasks";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { refreshMyOpenTasks } from "@/lib/hooks/use-my-open-tasks";
import { localInputToApiDateTime } from "@/lib/utils/datetime-api";
import { formatDateTime } from "@/lib/utils/format-date";
import type { Staff } from "@/types/staff";
import type { TaskResponseDto } from "@/types/api";

const selectClassName = cn(
  "flex h-9 w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-2 py-1 text-xs",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

const PRIORITIES: TaskResponseDto["priority"][] = [
  "LOW",
  "MEDIUM",
  "HIGH",
  "URGENT",
];
const STATUSES: TaskResponseDto["status"][] = [
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

function toDateTimeLocal(value: string | null | undefined): string {
  if (!value) return "";
  return value.length >= 16 ? value.slice(0, 16) : value;
}

type Props = {
  caseId: string;
  task: TaskResponseDto;
  canEdit: boolean;
  onUpdated: (next: TaskResponseDto) => void;
};

export function CaseTaskRow({ caseId, task, canEdit, onUpdated }: Props) {
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  // For the inline status change we track a separate "busy" flag so the user
  // can flip the row-level status without opening the full editor.
  const [statusBusy, setStatusBusy] = useState(false);
  const [staff, setStaff] = useState<Staff[]>([]);

  const [title, setTitle] = useState(task.title);
  const [description, setDescription] = useState(task.description ?? "");
  const [priority, setPriority] =
    useState<TaskResponseDto["priority"]>(task.priority);
  const [status, setStatus] = useState<TaskResponseDto["status"]>(task.status);
  const [dueDate, setDueDate] = useState(toDateTimeLocal(task.dueDate));
  const [assignedToId, setAssignedToId] = useState(task.assignedTo?.id ?? "");

  useEffect(() => {
    if (!editing) return;
    let cancelled = false;
    (async () => {
      try {
        const [a, b] = await Promise.all([
          staffApi.list({ page: 0, size: 200, role: "LAWYER" }),
          staffApi.list({ page: 0, size: 200, role: "SENIOR_LAWYER" }),
        ]);
        if (cancelled) return;
        const by = new Map<string, Staff>();
        [...a.content, ...b.content].forEach((s) => {
          if (s.isActive) by.set(s.id, s);
        });
        setStaff(
          [...by.values()].sort((x, y) =>
            x.fullName.localeCompare(y.fullName, "ar"),
          ),
        );
      } catch {
        if (!cancelled) setStaff([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [editing]);

  function resetFromProp() {
    setTitle(task.title);
    setDescription(task.description ?? "");
    setPriority(task.priority);
    setStatus(task.status);
    setDueDate(toDateTimeLocal(task.dueDate));
    setAssignedToId(task.assignedTo?.id ?? "");
  }

  /** Quick status update without opening the full editor. */
  async function inlineStatusChange(next: TaskResponseDto["status"]) {
    if (next === task.status) return;
    setStatusBusy(true);
    try {
      const updated = await tasksApi.update(caseId, task.id, {
        title: task.title,
        description: task.description,
        priority: task.priority,
        status: next,
        dueDate: task.dueDate,
        assignedToId: task.assignedTo?.id ?? null,
      });
      onUpdated(updated);
      notifySuccess("تم تحديث الحالة");
      void refreshMyOpenTasks();
    } catch (e) {
      notifyApiError(e, "تعذر تحديث الحالة");
    } finally {
      setStatusBusy(false);
    }
  }

  async function submit() {
    const t = title.trim();
    if (!t) {
      notifyError("أدخل عنوان المهمة");
      return;
    }
    setSaving(true);
    try {
      const updated = await tasksApi.update(caseId, task.id, {
        title: t,
        description: description.trim() || null,
        priority,
        status,
        dueDate: dueDate.trim() ? localInputToApiDateTime(dueDate) : null,
        assignedToId: assignedToId || null,
      });
      onUpdated(updated);
      notifySuccess("تم حفظ تعديلات المهمة");
      void refreshMyOpenTasks();
      setEditing(false);
    } catch (e) {
      notifyApiError(e, "تعذر حفظ التعديلات");
    } finally {
      setSaving(false);
    }
  }

  if (!editing) {
    return (
      <TableRow>
        <TableCell>
          <div className="font-medium">{task.title}</div>
          {task.description ? (
            <div className="text-xs text-[var(--color-muted-foreground)]">
              {task.description}
            </div>
          ) : null}
        </TableCell>
        <TableCell>
          <CasePriorityBadge value={task.priority} />
        </TableCell>
        <TableCell>
          {canEdit ? (
            <select
              className={selectClassName}
              value={task.status}
              disabled={statusBusy}
              onChange={(e) =>
                void inlineStatusChange(
                  e.target.value as TaskResponseDto["status"],
                )
              }
              aria-label="تغيير حالة المهمة"
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {ST_AR[s]}
                </option>
              ))}
            </select>
          ) : (
            <Badge tone="navy" className="text-xs">
              {ST_AR[task.status]}
            </Badge>
          )}
        </TableCell>
        <TableCell className="ltr-nums text-xs text-[var(--color-muted-foreground)]">
          {task.dueDate ? formatDateTime(task.dueDate) : "—"}
        </TableCell>
        <TableCell className="text-sm">
          {task.assignedTo?.fullName ?? "—"}
        </TableCell>
        {canEdit ? (
          <TableCell className="text-end">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => {
                resetFromProp();
                setEditing(true);
              }}
              aria-label="تعديل المهمة"
            >
              <Pencil className="size-4" />
            </Button>
          </TableCell>
        ) : null}
      </TableRow>
    );
  }

  return (
    <TableRow>
      <TableCell colSpan={6} className="p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1 sm:col-span-2">
            <Label htmlFor={`et-title-${task.id}`}>العنوان *</Label>
            <Input
              id={`et-title-${task.id}`}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              disabled={saving}
            />
          </div>
          <div className="space-y-1 sm:col-span-2">
            <Label htmlFor={`et-desc-${task.id}`}>وصف</Label>
            <Textarea
              id={`et-desc-${task.id}`}
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
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {ST_AR[s]}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor={`et-due-${task.id}`}>الموعد</Label>
            <Input
              id={`et-due-${task.id}`}
              type="datetime-local"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              disabled={saving}
            />
          </div>
          <div className="space-y-1">
            <Label>مكلَّف</Label>
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
        <div className="mt-4 flex flex-wrap justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              resetFromProp();
              setEditing(false);
            }}
            disabled={saving}
          >
            إلغاء
          </Button>
          <Button
            type="button"
            variant="gold"
            size="sm"
            onClick={() => void submit()}
            disabled={saving}
          >
            {saving ? <Loader2 className="size-4 animate-spin" /> : "حفظ"}
          </Button>
        </div>
      </TableCell>
    </TableRow>
  );
}
