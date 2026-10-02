"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Loader2, Pencil, Trash2 } from "lucide-react";

import { TaskDueBadge } from "@/components/tasks/TaskDueBadge";
import { TaskRelationBadge } from "@/components/tasks/TaskRelationBadge";
import { SectionCard } from "@/components/shared/SectionCard";
import { CasePriorityBadge } from "@/components/shared/StatusBadge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";
import { staffApi } from "@/lib/api/staff";
import { tasksApi } from "@/lib/api/tasks";
import {
  ADMIN_TASK_TYPES,
  ADMIN_TASK_TYPE_AR,
  TASK_PRIORITIES,
  TASK_PRIORITY_AR,
  TASK_STATUSES,
  TASK_STATUS_AR,
  TASK_STATUS_TONE,
} from "@/lib/tasks/task-labels";
import {
  buildAdminTaskUpdate,
  toDateTimeLocal,
} from "@/lib/tasks/task-update";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { localInputToApiDateTime } from "@/lib/utils/datetime-api";
import { formatDateTime } from "@/lib/utils/format-date";
import type { AdminTaskTypeDto, TaskResponseDto } from "@/types/api";
import type { Staff } from "@/types/staff";

const selectClassName = cn(
  "flex h-9 w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-2 py-1 text-xs",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

type Props = {
  task: TaskResponseDto;
  canEdit: boolean;
  canDelete: boolean;
  deleting: boolean;
  onDelete: () => void;
  onUpdated: (next: TaskResponseDto) => void;
};

export function TaskListCard({
  task,
  canEdit,
  canDelete,
  deleting,
  onDelete,
  onUpdated,
}: Props) {
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [statusBusy, setStatusBusy] = useState(false);
  const [staff, setStaff] = useState<Staff[]>([]);

  const [title, setTitle] = useState(task.title);
  const [description, setDescription] = useState(task.description ?? "");
  const [priority, setPriority] =
    useState<TaskResponseDto["priority"]>(task.priority);
  const [status, setStatus] = useState<TaskResponseDto["status"]>(task.status);
  const [dueDate, setDueDate] = useState(toDateTimeLocal(task.dueDate));
  const [assignedToId, setAssignedToId] = useState(task.assignedTo?.id ?? "");
  const [adminTaskType, setAdminTaskType] = useState<AdminTaskTypeDto | "">(
    task.adminTaskType ?? "",
  );

  useEffect(() => {
    if (!editing) return;
    let cancelled = false;
    (async () => {
      try {
        const [a, b, sec] = await Promise.all([
          staffApi.list({ page: 0, size: 200, role: "LAWYER" }),
          staffApi.list({ page: 0, size: 200, role: "SENIOR_LAWYER" }),
          staffApi.list({ page: 0, size: 200, role: "SECRETARY" }),
        ]);
        if (cancelled) return;
        const by = new Map<string, Staff>();
        [...a.content, ...b.content, ...sec.content].forEach((s) => {
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
    setAdminTaskType(task.adminTaskType ?? "");
  }

  async function inlineStatusChange(next: TaskResponseDto["status"]) {
    if (next === task.status) return;
    setStatusBusy(true);
    try {
      const updated = await tasksApi.updateAdmin(
        task.id,
        buildAdminTaskUpdate(task, { status: next }),
      );
      onUpdated(updated);
      notifySuccess("تم تحديث الحالة");
    } catch (e) {
      notifyApiError(e, "تعذر تحديث الحالة");
    } finally {
      setStatusBusy(false);
    }
  }

  async function submitEdit() {
    const trimmed = title.trim();
    if (!trimmed) {
      notifyError("أدخل عنوان المهمة");
      return;
    }
    setSaving(true);
    try {
      const updated = await tasksApi.updateAdmin(
        task.id,
        buildAdminTaskUpdate(task, {
          title: trimmed,
          description: description.trim() || null,
          priority,
          status,
          dueDate: dueDate.trim() ? localInputToApiDateTime(dueDate) : null,
          assignedToId: assignedToId || null,
          adminTaskType: adminTaskType || null,
        }),
      );
      onUpdated(updated);
      notifySuccess("تم حفظ تعديلات المهمة");
      setEditing(false);
    } catch (e) {
      notifyApiError(e, "تعذر حفظ التعديلات");
    } finally {
      setSaving(false);
    }
  }

  if (editing) {
    return (
      <SectionCard className="p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1 sm:col-span-2">
            <Label htmlFor={`task-edit-title-${task.id}`}>العنوان *</Label>
            <Input
              id={`task-edit-title-${task.id}`}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              disabled={saving}
              maxLength={200}
            />
          </div>
          <div className="space-y-1 sm:col-span-2">
            <Label htmlFor={`task-edit-desc-${task.id}`}>الوصف</Label>
            <Textarea
              id={`task-edit-desc-${task.id}`}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              disabled={saving}
            />
          </div>
          <div className="space-y-1 sm:col-span-2">
            <Label>المهام الإدارية</Label>
            <select
              className={selectClassName}
              value={adminTaskType}
              onChange={(e) =>
                setAdminTaskType(e.target.value as AdminTaskTypeDto | "")
              }
              disabled={saving}
            >
              <option value="">— بدون —</option>
              {ADMIN_TASK_TYPES.map((type) => (
                <option key={type} value={type}>
                  {ADMIN_TASK_TYPE_AR[type]}
                </option>
              ))}
            </select>
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
              {TASK_PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {TASK_PRIORITY_AR[p]}
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
              {TASK_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {TASK_STATUS_AR[s]}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor={`task-edit-due-${task.id}`}>الموعد</Label>
            <Input
              id={`task-edit-due-${task.id}`}
              type="datetime-local"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              disabled={saving}
              dir="ltr"
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
            onClick={() => void submitEdit()}
            disabled={saving}
          >
            {saving ? <Loader2 className="size-4 animate-spin" /> : "حفظ"}
          </Button>
        </div>
      </SectionCard>
    );
  }

  return (
    <SectionCard className="p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-semibold text-[var(--color-foreground)]">
              {task.title}
            </span>
            {task.adminTaskType ? (
              <Badge tone="neutral" className="text-xs">
                {ADMIN_TASK_TYPE_AR[task.adminTaskType]}
              </Badge>
            ) : null}
            <TaskRelationBadge task={task} />
            <CasePriorityBadge value={task.priority} />
            {canEdit ? (
              <select
                className={cn(selectClassName, "h-7 w-auto min-w-[7.5rem]")}
                value={task.status}
                disabled={statusBusy}
                onChange={(e) =>
                  void inlineStatusChange(
                    e.target.value as TaskResponseDto["status"],
                  )
                }
                aria-label="تغيير حالة المهمة"
              >
                {TASK_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {TASK_STATUS_AR[s]}
                  </option>
                ))}
              </select>
            ) : (
              <Badge tone={TASK_STATUS_TONE[task.status]} className="text-xs">
                {TASK_STATUS_AR[task.status]}
              </Badge>
            )}
            <TaskDueBadge task={task} />
          </div>

          {task.description ? (
            <p className="text-sm text-[var(--color-muted-foreground)]">
              {task.description}
            </p>
          ) : null}

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 pt-0.5 text-xs text-[var(--color-muted-foreground)]">
            {task.caseId && task.caseNumber ? (
              <Link
                href={`/cases/${task.caseId}`}
                className="inline-flex items-center gap-1 rounded-md bg-[var(--color-surface-muted)] px-2 py-0.5 font-mono font-medium text-[var(--color-navy-700)] hover:text-[var(--color-gold-600)] transition-colors"
              >
                قضية #{task.caseNumber}
              </Link>
            ) : null}
            {task.caseTitle ? (
              <span className="truncate max-w-[200px]">{task.caseTitle}</span>
            ) : null}
            {task.disputeId && task.disputeNumber ? (
              <Link
                href={`/disputes/${task.disputeId}`}
                className="inline-flex items-center gap-1 rounded-md bg-[var(--color-surface-muted)] px-2 py-0.5 font-mono font-medium text-[var(--color-navy-700)] hover:text-[var(--color-gold-600)] transition-colors"
              >
                نزاع #{task.disputeNumber}
              </Link>
            ) : null}
            {task.dueDate ? (
              <span className="ltr-nums">
                الموعد: {formatDateTime(task.dueDate)}
              </span>
            ) : (
              <span>بدون موعد</span>
            )}
            {task.assignedTo ? (
              <span>مكلَّف: {task.assignedTo.fullName}</span>
            ) : (
              <span>غير مُسندة</span>
            )}
            {task.createdBy ? (
              <span>أنشأها: {task.createdBy.fullName}</span>
            ) : null}
            {task.status === "DONE" && task.completedAt ? (
              <span className="ltr-nums">
                أُنجزت: {formatDateTime(task.completedAt)}
              </span>
            ) : null}
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          {task.caseId ? (
            <Button variant="outline" size="sm" asChild>
              <Link href={`/cases/${task.caseId}`}>فتح القضية</Link>
            </Button>
          ) : task.disputeId ? (
            <Button variant="outline" size="sm" asChild>
              <Link href={`/disputes/${task.disputeId}`}>فتح النزاع</Link>
            </Button>
          ) : null}
          {canEdit ? (
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
          ) : null}
          {canDelete ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={onDelete}
              disabled={deleting}
              className="text-[var(--color-danger)] hover:bg-red-50 hover:text-[var(--color-danger)]"
              aria-label="حذف المهمة"
            >
              {deleting ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Trash2 className="size-4" />
              )}
            </Button>
          ) : null}
        </div>
      </div>
    </SectionCard>
  );
}
