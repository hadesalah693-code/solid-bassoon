"use client";

import { useCallback, useEffect, useState } from "react";
import { Columns3, LayoutList, RefreshCw } from "lucide-react";

import { TaskCreateForm } from "@/components/tasks/TaskCreateForm";
import { TaskKanbanBoard } from "@/components/tasks/TaskKanbanBoard";
import { TaskListCard } from "@/components/tasks/TaskListCard";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { Pagination } from "@/components/shared/Pagination";
import { Button } from "@/components/ui/button";
import { ApiRequestError } from "@/lib/api/client";
import {
  TASKS_KANBAN_SIZE,
  TASKS_PAGE_SIZE,
  tasksApi,
} from "@/lib/api/tasks";
import { useAuth } from "@/components/auth/auth-provider";
import { refreshMyOpenTasks } from "@/lib/hooks/use-my-open-tasks";
import {
  ADMIN_TASK_TYPES,
  ADMIN_TASK_TYPE_AR,
  TASK_STATUSES,
  TASK_STATUS_AR,
} from "@/lib/tasks/task-labels";
import { buildAdminTaskUpdate } from "@/lib/tasks/task-update";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import type { AdminTaskTypeDto, TaskResponseDto } from "@/types/api";
import type { SystemRole } from "@/types/staff";
import { ClipboardList } from "lucide-react";

type TaskStatus = TaskResponseDto["status"];
type ScopeFilter = "MINE" | "ALL" | "UNASSIGNED";
type ViewMode = "list" | "kanban";

const SCOPE_OPTIONS: { value: ScopeFilter; label: string }[] = [
  { value: "MINE", label: "مهامي" },
  { value: "ALL", label: "الكل" },
  { value: "UNASSIGNED", label: "غير مُسندة" },
];

function canDeleteRole(role: SystemRole | null) {
  return role === "ADMIN" || role === "SENIOR_LAWYER";
}

function canEditRole(role: SystemRole | null) {
  return (
    role === "ADMIN" ||
    role === "SENIOR_LAWYER" ||
    role === "LAWYER" ||
    role === "SECRETARY"
  );
}

function canCreateRole(role: SystemRole | null) {
  return canEditRole(role);
}

export default function TasksPage() {
  const { user } = useAuth();
  const role = (user?.systemRole as SystemRole | undefined) ?? null;
  const [viewMode, setViewMode] = useState<ViewMode>("list");
  const [tasks, setTasks] = useState<TaskResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filterStatus, setFilterStatus] = useState<TaskStatus | "ALL">("ALL");
  const [filterAdminType, setFilterAdminType] = useState<
    AdminTaskTypeDto | "ALL"
  >("ALL");
  const [scopeFilter, setScopeFilter] = useState<ScopeFilter>("MINE");
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [statusBusyId, setStatusBusyId] = useState<string | null>(null);
  const [loadKey, setLoadKey] = useState(0);

  const loadAllTasks = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const isKanban = viewMode === "kanban";
      const result = await tasksApi.list({
        page: isKanban ? 0 : page,
        size: isKanban ? TASKS_KANBAN_SIZE : TASKS_PAGE_SIZE,
        status:
          isKanban || filterStatus === "ALL" ? undefined : filterStatus,
        assignedToId:
          scopeFilter === "MINE" && user?.id ? user.id : undefined,
        unassignedOnly: scopeFilter === "UNASSIGNED" ? true : undefined,
        adminTaskType:
          filterAdminType === "ALL" ? undefined : filterAdminType,
      });
      const items = isKanban
        ? result.content.filter((t) => t.status !== "CANCELLED")
        : result.content;
      setTasks(items);
      setTotalElements(
        isKanban ? items.length : result.totalElements,
      );
      void refreshMyOpenTasks();
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setError(e.details?.message ?? e.message);
      } else {
        setError("تعذر تحميل المهام");
      }
    } finally {
      setLoading(false);
    }
  }, [filterStatus, filterAdminType, scopeFilter, user?.id, page, viewMode]);

  useEffect(() => {
    void loadAllTasks();
  }, [loadAllTasks, loadKey]);

  async function handleDelete(task: TaskResponseDto) {
    if (!window.confirm(`حذف المهمة "${task.title}"؟`)) return;
    setDeletingId(task.id);
    try {
      await tasksApi.delete(task.caseId, task.id);
      setTasks((prev) => prev.filter((t) => t.id !== task.id));
      setTotalElements((t) => Math.max(0, t - 1));
      notifySuccess("تم حذف المهمة");
      void refreshMyOpenTasks();
    } catch (e) {
      notifyApiError(e, "تعذر حذف المهمة");
    } finally {
      setDeletingId(null);
    }
  }

  function handleUpdated(next: TaskResponseDto) {
    setTasks((prev) => {
      const inScope =
        scopeFilter === "UNASSIGNED"
          ? !next.assignedTo
          : scopeFilter === "MINE"
            ? next.assignedTo?.id === user?.id
            : true;
      const matchesStatus =
        viewMode === "kanban" ||
        filterStatus === "ALL" ||
        next.status === filterStatus;
      const matchesAdminType =
        filterAdminType === "ALL" || next.adminTaskType === filterAdminType;
      if (
        !inScope ||
        !matchesStatus ||
        !matchesAdminType ||
        next.status === "CANCELLED"
      ) {
        if (viewMode === "list") {
          setTotalElements((t) => Math.max(0, t - 1));
        }
        return prev.filter((t) => t.id !== next.id);
      }
      return prev.map((t) => (t.id === next.id ? next : t));
    });
    void refreshMyOpenTasks();
  }

  async function handleKanbanStatusChange(
    task: TaskResponseDto,
    status: TaskResponseDto["status"],
  ) {
    setStatusBusyId(task.id);
    try {
      const updated = await tasksApi.updateAdmin(
        task.id,
        buildAdminTaskUpdate(task, { status }),
      );
      handleUpdated(updated);
      notifySuccess("تم تحديث الحالة");
    } catch (e) {
      notifyApiError(e, "تعذر تحديث الحالة");
    } finally {
      setStatusBusyId(null);
    }
  }

  const emptyDescription =
    scopeFilter === "MINE"
      ? viewMode === "kanban" || filterStatus === "ALL"
        ? "لا مهام مُسندة إليك."
        : `لا مهام مُسندة إليك بحالة «${TASK_STATUS_AR[filterStatus as TaskStatus]}».`
      : scopeFilter === "UNASSIGNED"
        ? "لا مهام بدون تعيين."
        : viewMode === "kanban" || filterStatus === "ALL"
          ? "لا توجد مهام مسجّلة."
          : `لا مهام بحالة «${TASK_STATUS_AR[filterStatus as TaskStatus]}».`;

  const showPagination =
    viewMode === "list" && totalElements > TASKS_PAGE_SIZE;

  return (
    <div className="space-y-6">
      <PageHeader
        title="المهام"
        description="جميع مهام المكتب — قضايا، نزاعات، ومهام إدارية."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-0.5">
              <Button
                type="button"
                variant={viewMode === "list" ? "gold" : "ghost"}
                size="sm"
                onClick={() => {
                  setViewMode("list");
                  setPage(0);
                }}
              >
                <LayoutList className="size-4" />
                قائمة
              </Button>
              <Button
                type="button"
                variant={viewMode === "kanban" ? "gold" : "ghost"}
                size="sm"
                onClick={() => {
                  setViewMode("kanban");
                  setPage(0);
                }}
              >
                <Columns3 className="size-4" />
                لوحة
              </Button>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setLoadKey((k) => k + 1)}
              disabled={loading}
            >
              <RefreshCw
                className={`size-4 ${loading ? "animate-spin" : ""}`}
              />
              تحديث
            </Button>
          </div>
        }
      />

      {canCreateRole(role) ? (
        <TaskCreateForm onCreated={() => setLoadKey((k) => k + 1)} />
      ) : null}

      <div className="flex flex-wrap gap-2">
        {SCOPE_OPTIONS.map(({ value, label }) => (
          <button
            key={value}
            type="button"
            onClick={() => {
              setScopeFilter(value);
              setPage(0);
            }}
            className={`inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-xs font-medium transition-colors ${
              scopeFilter === value
                ? "border-[var(--color-navy-700)] bg-[var(--color-navy-700)] text-white"
                : "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-foreground)] hover:bg-[var(--color-surface-muted)]"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <label
          htmlFor="admin-task-type-filter"
          className="text-xs font-medium text-[var(--color-muted-foreground)]"
        >
          المهام الإدارية:
        </label>
        <select
          id="admin-task-type-filter"
          value={filterAdminType}
          onChange={(e) => {
            setFilterAdminType(e.target.value as AdminTaskTypeDto | "ALL");
            setPage(0);
          }}
          className="h-9 min-w-[14rem] max-w-full cursor-pointer rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-xs"
        >
          <option value="ALL">كل الأنواع</option>
          {ADMIN_TASK_TYPES.map((type) => (
            <option key={type} value={type}>
              {ADMIN_TASK_TYPE_AR[type]}
            </option>
          ))}
        </select>
      </div>

      {viewMode === "list" ? (
        <div className="flex flex-wrap gap-2">
          {(["ALL", ...TASK_STATUSES] as const).map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => {
                setFilterStatus(s);
                setPage(0);
              }}
              className={`inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-xs font-medium transition-colors ${
                filterStatus === s
                  ? "border-[var(--color-gold-500)] bg-[var(--color-gold-500)] text-[var(--color-navy-900)]"
                  : "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-foreground)] hover:bg-[var(--color-surface-muted)]"
              }`}
            >
              {s === "ALL" ? "كل الحالات" : TASK_STATUS_AR[s]}
            </button>
          ))}
        </div>
      ) : (
        <p className="text-xs text-[var(--color-muted-foreground)]">
          اسحب المهمة بين الأعمدة لتغيير حالتها. تُعرض حتى{" "}
          {TASKS_KANBAN_SIZE} مهمة حسب الفلتر الحالي.
        </p>
      )}

      {error ? (
        <ApiErrorAlert
          message={error}
          onRetry={() => setLoadKey((k) => k + 1)}
        />
      ) : null}

      {loading ? (
        <PageLoading label="جارٍ تحميل المهام…" minHeightClassName="min-h-[16rem]" />
      ) : !error && tasks.length === 0 ? (
        <EmptyState
          title="لا مهام"
          description={emptyDescription}
          icon={ClipboardList}
        />
      ) : viewMode === "kanban" ? (
        <TaskKanbanBoard
          tasks={tasks}
          canEdit={canEditRole(role)}
          busyTaskId={statusBusyId}
          onStatusChange={handleKanbanStatusChange}
        />
      ) : (
        <>
          <div className="space-y-3">
            {tasks.map((task) => (
              <TaskListCard
                key={task.id}
                task={task}
                canEdit={canEditRole(role)}
                canDelete={canDeleteRole(role)}
                deleting={deletingId === task.id}
                onDelete={() => void handleDelete(task)}
                onUpdated={handleUpdated}
              />
            ))}
          </div>
          {showPagination ? (
            <Pagination
              page={page}
              pageSize={TASKS_PAGE_SIZE}
              total={totalElements}
              onPageChange={setPage}
            />
          ) : totalElements > 0 ? (
            <p className="px-2 text-sm text-[var(--color-muted-foreground)] ltr-nums">
              {totalElements} {totalElements === 1 ? "مهمة" : "مهام"}
            </p>
          ) : null}
        </>
      )}
    </div>
  );
}
