import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type { MemoResponse, MemoStatus, MemoType, MemoWriteRequest } from "@/types/memo";

export const memosApi = {
  list: (query: { caseId?: string; status?: MemoStatus; type?: MemoType; search?: string; page?: number; size?: number } = {}) =>
    request<PagedResponse<MemoResponse>>("/memos", {
      query: {
        page: query.page ?? 0,
        size: query.size ?? 20,
        ...(query.caseId ? { caseId: query.caseId } : {}),
        ...(query.status ? { status: query.status } : {}),
        ...(query.type ? { type: query.type } : {}),
        ...(query.search ? { search: query.search } : {}),
      },
    }),
  get: (id: string) => request<MemoResponse>(`/memos/${id}`),
  create: (caseId: string, body: MemoWriteRequest) => request<MemoResponse>(`/memos?caseId=${encodeURIComponent(caseId)}`, { method: "POST", body }),
  update: (id: string, body: MemoWriteRequest) => request<MemoResponse>(`/memos/${id}`, { method: "PUT", body }),
  submit: (id: string) => request<MemoResponse>(`/memos/${id}/submit`, { method: "POST" }),
  approve: (id: string) => request<MemoResponse>(`/memos/${id}/approve`, { method: "POST" }),
  send: (id: string) => request<MemoResponse>(`/memos/${id}/send`, { method: "POST" }),
  archive: (id: string) => request<MemoResponse>(`/memos/${id}/archive`, { method: "POST" }),
};