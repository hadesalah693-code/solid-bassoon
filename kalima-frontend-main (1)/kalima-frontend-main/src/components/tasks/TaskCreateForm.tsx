"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2, Plus } from "lucide-react";

import { TaskRelationSelector } from "@/components/tasks/TaskRelationSelector";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";
import { staffApi } from "@/lib/api/staff";
import { tasksApi } from "@/lib/api/tasks";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { refreshMyOpenTasks } from "@/lib/hooks/use-my-open-tasks";
import {
  relationTypeFromDefaults,
  type TaskRelationType,
} from "@/lib/tasks/task-relation";
import { localInputToApiDateTime } from "@/lib/utils/datetime-api";
import type { Staff } from "@/types/staff";
import type { AdminTaskTypeDto, TaskResponseDto } from "@/types/api";
import {
  ADMIN_TASK_TYPES,
  ADMIN_TASK_TYPE_AR,
} from "@/lib/tasks/task-labels";

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

type Props = {
  defaultCaseId?: string | null;
  defaultDisputeId?: string | null;
  disabled?: boolean;
  onCreated?: () => void;
};

export function TaskCreateForm({
  defaultCaseId = null,
  defaultDisputeId = null,
  disabled,
  onCreated,
}: Props) {
  const lockedRelationType = useMemo(() => {
    if (defaultCaseId) return "case" as const;
    if (defaultDisputeId) return "dispute" as const;
    return null;
  }, [defaultCaseId, defaultDisputeId]);

  const [staff, setStaff] = useState<Staff[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] =
    useState<TaskResponseDto["priority"]>("MEDIUM");
  const [status, setStatus] = useState<TaskResponseDto["status"]>("TODO");
  const [dueDate, setDueDate] = useState("");
  const [assignedToId, setAssignedToId] = useState("");
  const [adminTaskType, setAdminTaskType] = useState<AdminTaskTypeDto | "">("");
  const [saving, setSaving] = useState(false);

  const [relationType, setRelationType] = useState<TaskRelationType>(() =>
    relationTypeFromDefaults(defaultCaseId, defaultDisputeId),
  );
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(
    defaultCaseId,
  );
  const [selectedDisputeId, setSelectedDisputeId] = useState<string | null>(
    defaultDisputeId,
  );

  useEffect(() => {
    let c = false;
    (async () => {
      try {
        const [a, b, sec] = await Promise.all([
          staffApi.list({ page: 0, size: 200, role: "LAWYER" }),
          staffApi.list({ page: 0, size: 200, role: "SENIOR_LAWYER" }),
          staffApi.list({ page: 0, size: 200, role: "SECRETARY" }),
        ]);
        if (c) return;
        const by = new Map<string, Staff>();
        [...a.content, ...b.content, ...sec.content].forEach((s) => {
          if (s.isActive) by.set(s.id, s);
        });
        setStaff([...by.values()]);
      } catch {
        if (!c) setStaff([]);
      }
    })();
    return () => {
      c = true;
    };
  }, []);

  function resolveSubmitLinks(): {
    caseId: string | null;
    disputeId: string | null;
  } {
    if (lockedRelationType === "case") {
      return { caseId: defaultCaseId, disputeId: null };
    }
    if (lockedRelationType === "dispute") {
      return { caseId: null, disputeId: defaultDisputeId };
    }
    if (relationType === "case") {
      return { caseId: selectedCaseId, disputeId: null };
    }
    if (relationType === "dispute") {
      return { caseId: null, disputeId: selectedDisputeId };
    }
    return { caseId: null, disputeId: null };
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (disabled) return;
    const trimmed = title.trim();
    if (!trimmed) {
      notifyError("أدخل عنوان المهمة");
      return;
    }

    if (!lockedRelationType) {
      if (relationType === "case" && !selectedCaseId) {
        notifyError("اختر قضية للربط");
        return;
      }
      if (relationType === "dispute" && !selectedDisputeId) {
        notifyError("اختر نزاعاً للربط");
        return;
      }
    }

    const { caseId, disputeId } = resolveSubmitLinks();

    setSaving(true);
    try {
      await tasksApi.createAdmin({
        title: trimmed,
        description: description.trim() || null,
        priority,
        status,
        dueDate: dueDate ? localInputToApiDateTime(dueDate) : null,
        assignedToId: assignedToId || null,
        caseId,
        disputeId,
        adminTaskType: adminTaskType || null,
      });
      notifySuccess("تم إنشاء المهمة");
      void refreshMyOpenTasks();
      setTitle("");
      setDescription("");
      setDueDate("");
      setAssignedToId("");
      setAdminTaskType("");
      setPriority("MEDIUM");
      setStatus("TODO");
      if (!lockedRelationType) {
        setRelationType("none");
        setSelectedCaseId(null);
        setSelectedDisputeId(null);
      }
      onCreated?.();
    } catch (err) {
      notifyApiError(err, "تعذر إنشاء المهمة");
    } finally {
      setSaving(false);
    }
  }

  return (
    <SectionCard title="مهمة جديدة" className="p-4">
      <form onSubmit={(e) => void onSubmit(e)} className="space-y-3">
        <div className="grid gap-3 md:grid-cols-2">
          <TaskRelationSelector
            relationType={relationType}
            onRelationTypeChange={setRelationType}
            selectedCaseId={selectedCaseId}
            onSelectedCaseIdChange={setSelectedCaseId}
            selectedDisputeId={selectedDisputeId}
            onSelectedDisputeIdChange={setSelectedDisputeId}
            disabled={disabled || saving}
            lockedRelationType={lockedRelationType}
          />

          <div className="space-y-1.5 md:col-span-2">
            <Label htmlFor="admin-task-type">المهام الإدارية</Label>
            <select
              id="admin-task-type"
              className={selectClassName}
              value={adminTaskType}
              disabled={disabled || saving}
              onChange={(e) => {
                const next = e.target.value as AdminTaskTypeDto | "";
                setAdminTaskType(next);
                if (next && !title.trim()) {
                  setTitle(ADMIN_TASK_TYPE_AR[next]);
                }
              }}
            >
              <option value="">— اختر نوع المهمة —</option>
              {ADMIN_TASK_TYPES.map((type) => (
                <option key={type} value={type}>
                  {ADMIN_TASK_TYPE_AR[type]}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5 md:col-span-2">
            <Label htmlFor="admin-task-title">العنوان</Label>
            <Input
              id="admin-task-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              disabled={disabled || saving}
              maxLength={200}
            />
          </div>
          <div className="space-y-1.5 md:col-span-2">
            <Label htmlFor="admin-task-desc">الوصف</Label>
            <Textarea
              id="admin-task-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={disabled || saving}
              rows={2}
            />
          </div>
          <div className="space-y-1.5">
            <Label>الأولوية</Label>
            <select
              className={selectClassName}
              value={priority}
              disabled={disabled || saving}
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
          <div className="space-y-1.5">
            <Label>الحالة</Label>
            <select
              className={selectClassName}
              value={status}
              disabled={disabled || saving}
              onChange={(e) =>
                setStatus(e.target.value as TaskResponseDto["status"])
              }
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {ST_AR[s]}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="admin-task-due">الموعد</Label>
            <Input
              id="admin-task-due"
              type="datetime-local"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              disabled={disabled || saving}
              dir="ltr"
            />
          </div>
          <div className="space-y-1.5">
            <Label>تكليف</Label>
            <select
              className={selectClassName}
              value={assignedToId}
              disabled={disabled || saving}
              onChange={(e) => setAssignedToId(e.target.value)}
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
        <Button type="submit" size="sm" disabled={disabled || saving}>
          {saving ? (
            <>
              <Loader2 className="size-4 animate-spin" />
              جارٍ الحفظ…
            </>
          ) : (
            <>
              <Plus className="size-4" />
              إضافة
            </>
          )}
        </Button>
      </form>
    </SectionCard>
  );
}
