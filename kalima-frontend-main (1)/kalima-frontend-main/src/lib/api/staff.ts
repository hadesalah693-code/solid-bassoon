import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  Staff,
  StaffCreateRequest,
  StaffUpdateRequest,
} from "@/types/staff";

/** Matches GET /api/staff: pagination + optional `role` only (no text search in backend yet). */
export type StaffQuery = {
  page?: number;
  size?: number;
  role?: string;
};

export const staffApi = {
  list: (q: StaffQuery = {}) =>
    request<PagedResponse<Staff>>("/staff", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.role ? { role: q.role } : {}),
      },
    }),
  get: (id: string) => request<Staff>(`/staff/${id}`),
  create: (body: StaffCreateRequest) =>
    request<Staff>("/staff", { method: "POST", body }),
  update: (id: string, body: StaffUpdateRequest) =>
    request<Staff>(`/staff/${id}`, { method: "PUT", body }),
  /** Soft-disable. 204 No Content. */
  disable: (id: string) => request<void>(`/staff/${id}`, { method: "DELETE" }),
  /** Re-enable. 204 No Content. */
  enable: (id: string) =>
    request<void>(`/staff/${id}/enable`, { method: "POST" }),
  /** Admin reset. 204 No Content. */
  resetPassword: (id: string, body: { newPassword: string }) =>
    request<void>(`/staff/${id}/reset-password`, {
      method: "POST",
      body,
    }),
  /**
   * POST /api/staff/me/change-password — any authenticated user.
   * Backend body shape: `{ oldPassword, newPassword }`. 204 No Content.
   */
  changeOwnPassword: (body: { oldPassword: string; newPassword: string }) =>
    request<void>(`/staff/me/change-password`, {
      method: "POST",
      body,
    }),
};
