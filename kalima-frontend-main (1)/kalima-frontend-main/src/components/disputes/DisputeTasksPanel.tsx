"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Loader2, Trash2 } from "lucide-react";

import { TaskCreateForm } from "@/components/tasks/TaskCreateForm";
import { TaskRelationBadge } from "@/components/tasks/TaskRelationBadge";
import { SectionCard } from "@/components/shared/SectionCard";
import { CasePriorityBadge } from "@/components/shared/StatusBadge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/components/auth/auth-provider";
import { tasksApi } from "@/lib/api/tasks";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDateTime } from "@/lib/utils/format-date";
import type { TaskResponseDto } from "@/types/api";
import type { SystemRole } from "@/types/staff";

const STATUS_AR: Record<TaskResponseDto["status"], string> = {
  TODO: "قيد الانتظار",
  IN_PROGRESS: "قيد التنفيذ",
  DONE: "منجز",
  CANCELLED: "ملغاة",
};

const STATUS_TONE: Record<
  TaskResponseDto["status"],
  "navy" | "info" | "success" | "neutral"
> = {
  TODO: "info",
  IN_PROGRESS: "navy",
  DONE: "success",
  CANCELLED: "neutral",
};

type Props = {
  disputeId: string;
  canEdit?: boolean;
};

export function DisputeTasksPanel({ disputeId, canEdit = true }: Props) {
  const { user } = useAuth();
  const role = (user?.systemRole as SystemRole | undefined) ?? null;
  const canDelete = role === "ADMIN" || role === "SENIOR_LAWYER";
  const canCreate =
    canEdit &&
    (role === "ADMIN" ||
      role === "SENIOR_LAWYER" ||
      role === "LAWYER" ||
      role === "SECRETARY");

  const [tasks, setTasks] = useState<TaskResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await tasksApi.listByDispute(disputeId, { size: 100 });
      setTasks(page.content);
    } catch {
      setTasks([]);
    } finally {
      setLoading(false);
    }
  }, [disputeId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleDelete(task: TaskResponseDto) {
    if (!window.confirm(`حذف المهمة "${task.title}"؟`)) return;
    setDeletingId(task.id);
    try {
      await tasksApi.delete(task.caseId, task.id);
      setTasks((prev) => prev.filter((t) => t.id !== task.id));
      notifySuccess("تم حذف المهمة");
    } catch (e) {
      notifyApiError(e, "تعذر حذف المهمة");
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <SectionCard
      title="مهام إدارية"
      description="مهام عامة مرتبطة بهذا النزاع (منفصلة عن إجراءات المراحل)."
    >
      {canCreate ? (
        <TaskCreateForm
          defaultDisputeId={disputeId}
          onCreated={() => void load()}
        />
      ) : null}

      {loading ? (
        <p className="mt-4 text-sm text-[var(--color-muted-foreground)]">
          جارٍ التحميل…
        </p>
      ) : tasks.length === 0 ? (
        <p className="mt-4 text-sm text-[var(--color-muted-foreground)]">
          لا مهام إدارية مرتبطة بهذا النزاع.
        </p>
      ) : (
        <ul className="mt-4 divide-y divide-[var(--color-border)]">
          {tasks.map((task) => (
            <li
              key={task.id}
              className="flex flex-wrap items-start justify-between gap-3 py-3 first:pt-0 last:pb-0"
            >
              <div className="min-w-0 space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{task.title}</span>
                  <TaskRelationBadge task={task} />
                  <CasePriorityBadge value={task.priority} />
                  <Badge tone={STATUS_TONE[task.status]} className="text-xs">
                    {STATUS_AR[task.status]}
                  </Badge>
                </div>
                <p className="text-xs text-[var(--color-muted-foreground)]">
                  {task.dueDate
                    ? `الموعد: ${formatDateTime(task.dueDate)}`
                    : "بدون موعد"}
                  {task.assignedTo ? ` · ${task.assignedTo.fullName}` : ""}
                </p>
                {task.caseId ? (
                  <Link
                    href={`/cases/${task.caseId}`}
                    className="text-xs text-[var(--color-gold-600)] hover:underline"
                  >
                    قضية #{task.caseNumber}
                  </Link>
                ) : null}
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" asChild>
                  <Link href="/tasks">عرض في المهام</Link>
                </Button>
                {canDelete ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => void handleDelete(task)}
                    disabled={deletingId === task.id}
                    className="text-[var(--color-danger)]"
                    aria-label="حذف"
                  >
                    {deletingId === task.id ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <Trash2 className="size-4" />
                    )}
                  </Button>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}
