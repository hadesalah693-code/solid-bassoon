"use client";

import Link from "next/link";
import { useMemo, useState } from "react";

import { TaskDueBadge } from "@/components/tasks/TaskDueBadge";
import { CasePriorityBadge } from "@/components/shared/StatusBadge";
import { cn } from "@/lib/utils/cn";
import { KANBAN_COLUMNS } from "@/lib/api/tasks";
import { ADMIN_TASK_TYPE_AR, TASK_STATUS_AR } from "@/lib/tasks/task-labels";
import { formatDateTime } from "@/lib/utils/format-date";
import { Badge } from "@/components/ui/badge";
import type { TaskResponseDto } from "@/types/api";

type Props = {
  tasks: TaskResponseDto[];
  canEdit: boolean;
  onStatusChange: (
    task: TaskResponseDto,
    status: TaskResponseDto["status"],
  ) => void | Promise<void>;
  busyTaskId?: string | null;
};

export function TaskKanbanBoard({
  tasks,
  canEdit,
  onStatusChange,
  busyTaskId,
}: Props) {
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [dropTarget, setDropTarget] = useState<TaskResponseDto["status"] | null>(
    null,
  );

  const byStatus = useMemo(() => {
    const map: Record<TaskResponseDto["status"], TaskResponseDto[]> = {
      TODO: [],
      IN_PROGRESS: [],
      DONE: [],
      CANCELLED: [],
    };
    for (const task of tasks) {
      if (task.status === "CANCELLED") continue;
      map[task.status].push(task);
    }
    return map;
  }, [tasks]);

  function handleDrop(
    column: TaskResponseDto["status"],
    taskId: string | null,
  ) {
    setDropTarget(null);
    setDraggingId(null);
    if (!canEdit || !taskId) return;
    const task = tasks.find((t) => t.id === taskId);
    if (!task || task.status === column) return;
    void onStatusChange(task, column);
  }

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      {KANBAN_COLUMNS.map((column) => (
        <div
          key={column}
          className={cn(
            "flex min-h-[16rem] flex-col rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40",
            dropTarget === column &&
              canEdit &&
              "ring-2 ring-[var(--color-gold-400)]",
          )}
          onDragOver={(e) => {
            if (!canEdit) return;
            e.preventDefault();
            setDropTarget(column);
          }}
          onDragLeave={() => setDropTarget(null)}
          onDrop={(e) => {
            e.preventDefault();
            handleDrop(column, e.dataTransfer.getData("text/task-id") || null);
          }}
        >
          <div className="flex items-center justify-between border-b border-[var(--color-border)] px-3 py-2.5">
            <h3 className="text-sm font-semibold text-[var(--color-foreground)]">
              {TASK_STATUS_AR[column]}
            </h3>
            <span className="rounded-full bg-[var(--color-surface)] px-2 py-0.5 text-xs tabular-nums text-[var(--color-muted-foreground)]">
              {byStatus[column].length}
            </span>
          </div>
          <div className="flex flex-1 flex-col gap-2 p-2">
            {byStatus[column].length === 0 ? (
              <p className="py-6 text-center text-xs text-[var(--color-muted-foreground)]">
                لا مهام
              </p>
            ) : (
              byStatus[column].map((task) => (
                <article
                  key={task.id}
                  draggable={canEdit && busyTaskId !== task.id}
                  onDragStart={(e) => {
                    if (!canEdit) return;
                    setDraggingId(task.id);
                    e.dataTransfer.setData("text/task-id", task.id);
                    e.dataTransfer.effectAllowed = "move";
                  }}
                  onDragEnd={() => {
                    setDraggingId(null);
                    setDropTarget(null);
                  }}
                  className={cn(
                    "rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-3 shadow-[var(--shadow-soft)] transition-opacity",
                    draggingId === task.id && "opacity-50",
                    busyTaskId === task.id && "opacity-60",
                    canEdit && "cursor-grab active:cursor-grabbing",
                  )}
                >
                  <div className="flex flex-wrap items-center gap-1.5">
                    <CasePriorityBadge value={task.priority} />
                    <TaskDueBadge task={task} />
                  </div>
                  <p className="mt-1.5 text-sm font-medium leading-snug text-[var(--color-foreground)]">
                    {task.title}
                  </p>
                  {task.adminTaskType ? (
                    <Badge tone="neutral" className="mt-1.5 text-[10px]">
                      {ADMIN_TASK_TYPE_AR[task.adminTaskType]}
                    </Badge>
                  ) : null}
                  <div className="mt-2 space-y-0.5 text-xs text-[var(--color-muted-foreground)]">
                    {task.dueDate ? (
                      <p className="ltr-nums">
                        الموعد: {formatDateTime(task.dueDate)}
                      </p>
                    ) : null}
                    {task.assignedTo ? (
                      <p>{task.assignedTo.fullName}</p>
                    ) : (
                      <p>غير مُسندة</p>
                    )}
                    {task.caseId && task.caseNumber ? (
                      <Link
                        href={`/cases/${task.caseId}`}
                        className="inline-block text-[var(--color-gold-600)] hover:underline"
                        onClick={(e) => e.stopPropagation()}
                      >
                        قضية #{task.caseNumber}
                      </Link>
                    ) : null}
                    {task.disputeId && task.disputeNumber ? (
                      <Link
                        href={`/disputes/${task.disputeId}`}
                        className="inline-block text-[var(--color-gold-600)] hover:underline"
                        onClick={(e) => e.stopPropagation()}
                      >
                        نزاع #{task.disputeNumber}
                      </Link>
                    ) : null}
                  </div>
                </article>
              ))
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
