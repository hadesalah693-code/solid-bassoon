import { request } from "./client";
import type { PagedResponse } from "@/types/common";

/** Matches `NotificationResponse` from kalima-backend. */
export type NotificationDto = {
  id: string;
  type:
    | "HEARING_ADDED"
    | "HEARING_UPDATED"
    | "CASE_STATUS_CHANGED"
    | "NEW_MESSAGE"
    | "TASK_ASSIGNED"
    | "TASK_DUE_SOON"
    | "TASK_OVERDUE"
    | "CALENDAR_REMINDER"
    | "CALENDAR_EVENT_ASSIGNED"
    | "DELEGATION_EXPIRING"
    | "DELEGATION_CANCELLED"
    | "DELEGATION_MENTION"
    | "DELEGATION_REMINDER_ADDED"
    | "CIRCULATION_REQUEST";
  title: string;
  body: string;
  read: boolean;
  caseId: string | null;
  createdAt: string;
};

export const notificationsApi = {
  list: (q: { page?: number; size?: number } = {}) =>
    request<PagedResponse<NotificationDto>>("/notifications", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
      },
    }),
  unreadCount: () =>
    request<{ count: number }>("/notifications/unread-count"),
  markRead: (id: string) =>
    request<void>(`/notifications/${id}/read`, { method: "POST" }),
  readAll: () =>
    request<void>("/notifications/read-all", { method: "POST" }),
};
