import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  AdminTaskCreateRequestDto,
  AdminTaskUpdateRequestDto,
  TaskCreateRequestDto,
  TaskResponseDto,
  TaskUpdateRequestDto,
} from "@/types/api";

export type TaskListQuery = {
  status?: TaskResponseDto["status"];
  assignedToId?: string;
  caseId?: string;
  disputeId?: string;
  unassignedOnly?: boolean;
  adminTaskType?: TaskResponseDto["adminTaskType"];
  page?: number;
  size?: number;
};

export const TASKS_PAGE_SIZE = 20;
export const TASKS_KANBAN_SIZE = 100;

export const KANBAN_COLUMNS: TaskResponseDto["status"][] = [
  "TODO",
  "IN_PROGRESS",
  "DONE",
];

export const tasksApi = {
  /** Tenant-wide paginated list — GET /api/tasks */
  list: (q: TaskListQuery = {}) =>
    request<PagedResponse<TaskResponseDto>>("/tasks", {
      query: {
        status: q.status,
        assignedToId: q.assignedToId,
        caseId: q.caseId,
        disputeId: q.disputeId,
        unassignedOnly: q.unassignedOnly ? true : undefined,
        adminTaskType: q.adminTaskType ?? undefined,
        page: q.page ?? 0,
        size: q.size ?? TASKS_PAGE_SIZE,
      },
    }),

  myOpenCount: () => request<{ count: number }>("/tasks/my-open-count"),

  createAdmin: (body: AdminTaskCreateRequestDto) =>
    request<TaskResponseDto>("/tasks", { method: "POST", body }),

  getById: (taskId: string) =>
    request<TaskResponseDto>(`/tasks/${taskId}`),

  updateAdmin: (taskId: string, body: AdminTaskUpdateRequestDto) =>
    request<TaskResponseDto>(`/tasks/${taskId}`, {
      method: "PUT",
      body,
    }),

  deleteAdmin: (taskId: string) =>
    request<void>(`/tasks/${taskId}`, { method: "DELETE" }),

  create: (caseId: string, body: TaskCreateRequestDto) =>
    request<TaskResponseDto>(`/cases/${caseId}/tasks`, {
      method: "POST",
      body,
    }),

  get: (caseId: string, taskId: string) =>
    request<TaskResponseDto>(`/cases/${caseId}/tasks/${taskId}`),

  update: (caseId: string, taskId: string, body: TaskUpdateRequestDto) =>
    request<TaskResponseDto>(`/cases/${caseId}/tasks/${taskId}`, {
      method: "PUT",
      body,
    }),

  listByCase: (caseId: string, q: { page?: number; size?: number } = {}) =>
    request<PagedResponse<TaskResponseDto>>(`/cases/${caseId}/tasks`, {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 50,
      },
    }),

  listByDispute: (disputeId: string, q: { page?: number; size?: number } = {}) =>
    tasksApi.list({
      disputeId,
      page: q.page ?? 0,
      size: q.size ?? 100,
    }),

  /** DELETE /api/cases/{caseId}/tasks/{taskId} or /api/tasks/{taskId} when no case. */
  delete: (caseId: string | null, taskId: string) =>
    caseId
      ? request<void>(`/cases/${caseId}/tasks/${taskId}`, { method: "DELETE" })
      : tasksApi.deleteAdmin(taskId),
};
