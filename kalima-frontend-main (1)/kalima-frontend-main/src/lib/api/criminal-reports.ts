import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  CriminalReportCreateRequestDto,
  CriminalReportListItemDto,
  CriminalReportResponseDto,
  CriminalReportStatusDto,
  CriminalReportStatusUpdateRequestDto,
  CriminalReportUpdateRequestDto,
} from "@/types/criminal-report";

export type CriminalReportsListQuery = {
  page?: number;
  size?: number;
  status?: CriminalReportStatusDto;
  clientId?: string;
  assignedLawyerId?: string;
  search?: string;
};

export const criminalReportsApi = {
  list: (q: CriminalReportsListQuery = {}) =>
    request<PagedResponse<CriminalReportListItemDto>>("/criminal-reports", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.status ? { status: q.status } : {}),
        ...(q.clientId ? { clientId: q.clientId } : {}),
        ...(q.assignedLawyerId ? { assignedLawyerId: q.assignedLawyerId } : {}),
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
      },
    }),

  get: (id: string) => request<CriminalReportResponseDto>(`/criminal-reports/${id}`),

  create: (body: CriminalReportCreateRequestDto) =>
    request<CriminalReportResponseDto>("/criminal-reports", { method: "POST", body }),

  update: (id: string, body: CriminalReportUpdateRequestDto) =>
    request<CriminalReportResponseDto>(`/criminal-reports/${id}`, { method: "PUT", body }),

  updateStatus: (id: string, body: CriminalReportStatusUpdateRequestDto) =>
    request<CriminalReportResponseDto>(`/criminal-reports/${id}/status`, {
      method: "PATCH",
      body,
    }),
};
