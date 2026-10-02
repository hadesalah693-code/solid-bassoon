import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type { CirculationCreateRequest, CirculationRequest, CirculationStatus } from "@/types/circulation";

type Action = (id: string, notes?: string) => Promise<CirculationRequest>;
const action = (id: string, suffix: string, notes?: string) => request<CirculationRequest>(`/circulation-requests/${id}/${suffix}`, { method: "POST", body: notes ? { notes } : undefined });

export const circulationApi = {
  inbox: (status?: CirculationStatus, page = 0, size = 20) => request<PagedResponse<CirculationRequest>>("/circulation-requests/inbox", { query: { page, size, ...(status ? { status } : {}) } }),
  outbox: (status?: CirculationStatus, page = 0, size = 20) => request<PagedResponse<CirculationRequest>>("/circulation-requests/outbox", { query: { page, size, ...(status ? { status } : {}) } }),
  forCase: (caseId: string, page = 0, size = 20) => request<PagedResponse<CirculationRequest>>(`/circulation-requests/case/${caseId}`, { query: { page, size } }),
  get: (id: string) => request<CirculationRequest>(`/circulation-requests/${id}`),
  create: (body: CirculationCreateRequest) => request<CirculationRequest>("/circulation-requests", { method: "POST", body }),
  approve: ((id, notes) => action(id, "approve", notes)) as Action,
  reject: ((id, notes) => action(id, "reject", notes)) as Action,
  handover: ((id, notes) => action(id, "handover", notes)) as Action,
  returnFile: ((id, notes) => action(id, "return", notes)) as Action,
  cancel: ((id, notes) => action(id, "cancel", notes)) as Action,
};