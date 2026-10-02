import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  LaborComplaintCreateRequestDto,
  LaborComplaintDocumentDto,
  LaborComplaintListItemDto,
  LaborComplaintResponseDto,
  LaborComplaintStatusDto,
  LaborComplaintStatusUpdateRequestDto,
  LaborComplaintUpdateRequestDto,
} from "@/types/labor-complaint";

export type LaborComplaintsListQuery = {
  page?: number;
  size?: number;
  status?: LaborComplaintStatusDto;
  clientId?: string;
  assignedLawyerId?: string;
  search?: string;
};

export const laborComplaintsApi = {
  list: (q: LaborComplaintsListQuery = {}) =>
    request<PagedResponse<LaborComplaintListItemDto>>("/labor-complaints", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.status ? { status: q.status } : {}),
        ...(q.clientId ? { clientId: q.clientId } : {}),
        ...(q.assignedLawyerId ? { assignedLawyerId: q.assignedLawyerId } : {}),
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
      },
    }),

  get: (id: string) => request<LaborComplaintResponseDto>(`/labor-complaints/${id}`),

  create: (body: LaborComplaintCreateRequestDto) =>
    request<LaborComplaintResponseDto>("/labor-complaints", { method: "POST", body }),

  update: (id: string, body: LaborComplaintUpdateRequestDto) =>
    request<LaborComplaintResponseDto>(`/labor-complaints/${id}`, { method: "PUT", body }),

  updateStatus: (id: string, body: LaborComplaintStatusUpdateRequestDto) =>
    request<LaborComplaintResponseDto>(`/labor-complaints/${id}/status`, {
      method: "PATCH",
      body,
    }),

  listDocuments: (id: string) =>
    request<LaborComplaintDocumentDto[]>(`/labor-complaints/${id}/documents`),

  uploadDocument: async (id: string, file: File, description?: string) => {
    const form = new FormData();
    form.append("file", file);
    const qs = description?.trim()
      ? `?description=${encodeURIComponent(description.trim())}`
      : "";
    const res = await authenticatedFetch(`/labor-complaints/${id}/documents${qs}`, {
      method: "POST",
      headers: { Accept: "application/json" },
      body: form,
    });
    return (await res.json()) as LaborComplaintDocumentDto;
  },

  documentDownloadUrl: (complaintId: string, documentId: string) =>
    `/api/labor-complaints/${complaintId}/documents/${documentId}/download`,

  deleteDocument: (complaintId: string, documentId: string) =>
    request<void>(`/labor-complaints/${complaintId}/documents/${documentId}`, {
      method: "DELETE",
    }),
};
