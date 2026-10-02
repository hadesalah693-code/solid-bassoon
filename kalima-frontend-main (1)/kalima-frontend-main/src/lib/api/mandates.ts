import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  MandateCreateRequestDto,
  MandateDocumentTypeDto,
  MandateDocumentDto,
  MandateEventDto,
  MandateListItemDto,
  MandatePaymentRequestDto,
  MandatePaymentDto,
  MandatePaymentStatusDto,
  MandatePriorityDto,
  MandateResponseDto,
  MandateSessionResultRequestDto,
  MandateStatisticsDto,
  MandateStatusDto,
  MandateStatusUpdateRequestDto,
  MandateUpdateRequestDto,
} from "@/types/mandate";

export type MandatesListQuery = {
  page?: number;
  size?: number;
  search?: string;
  status?: MandateStatusDto;
  priority?: MandatePriorityDto;
  paymentStatus?: MandatePaymentStatusDto;
  caseId?: string;
  originalLawyerId?: string;
  substitutedLawyerId?: string;
  courtName?: string;
  sessionFrom?: string;
  sessionTo?: string;
};

function exportQuery(q: Omit<MandatesListQuery, "page" | "size">) {
  const params = new URLSearchParams();
  if (q.status) params.set("status", q.status);
  if (q.priority) params.set("priority", q.priority);
  if (q.paymentStatus) params.set("paymentStatus", q.paymentStatus);
  if (q.caseId) params.set("caseId", q.caseId);
  if (q.originalLawyerId) params.set("originalLawyerId", q.originalLawyerId);
  if (q.substitutedLawyerId)
    params.set("substitutedLawyerId", q.substitutedLawyerId);
  if (q.courtName?.trim()) params.set("courtName", q.courtName.trim());
  if (q.sessionFrom) params.set("sessionFrom", q.sessionFrom);
  if (q.sessionTo) params.set("sessionTo", q.sessionTo);
  if (q.search?.trim()) params.set("search", q.search.trim());
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export const mandatesApi = {
  list: (q: MandatesListQuery = {}) =>
    request<PagedResponse<MandateListItemDto>>("/mandates", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
        ...(q.status ? { status: q.status } : {}),
        ...(q.priority ? { priority: q.priority } : {}),
        ...(q.paymentStatus ? { paymentStatus: q.paymentStatus } : {}),
        ...(q.caseId ? { caseId: q.caseId } : {}),
        ...(q.originalLawyerId ? { originalLawyerId: q.originalLawyerId } : {}),
        ...(q.substitutedLawyerId
          ? { substitutedLawyerId: q.substitutedLawyerId }
          : {}),
        ...(q.courtName ? { courtName: q.courtName } : {}),
        ...(q.sessionFrom ? { sessionFrom: q.sessionFrom } : {}),
        ...(q.sessionTo ? { sessionTo: q.sessionTo } : {}),
      },
    }),

  statistics: () =>
    request<MandateStatisticsDto>("/mandates/statistics"),

  exportCsv: async (q: Omit<MandatesListQuery, "page" | "size"> = {}) => {
    const res = await authenticatedFetch(`/mandates/export${exportQuery(q)}`);
    return res.blob();
  },

  get: (id: string) => request<MandateResponseDto>(`/mandates/${id}`),

  create: (body: MandateCreateRequestDto) =>
    request<MandateResponseDto>("/mandates", { method: "POST", body }),

  update: (id: string, body: MandateUpdateRequestDto) =>
    request<MandateResponseDto>(`/mandates/${id}`, { method: "PUT", body }),

  changeStatus: (id: string, body: MandateStatusUpdateRequestDto) =>
    request<MandateResponseDto>(`/mandates/${id}/status`, {
      method: "PATCH",
      body,
    }),

  recordSessionResult: (id: string, body: MandateSessionResultRequestDto) =>
    request<MandateResponseDto>(`/mandates/${id}/session-result`, {
      method: "POST",
      body,
    }),

  remove: (id: string) =>
    request<void>(`/mandates/${id}`, { method: "DELETE" }),

  listTimeline: (id: string) =>
    request<MandateEventDto[]>(`/mandates/${id}/timeline`),

  listPayments: (id: string) =>
    request<MandatePaymentDto[]>(`/mandates/${id}/payments`),

  recordPayment: (id: string, body: MandatePaymentRequestDto) =>
    request<MandatePaymentDto>(`/mandates/${id}/payments`, {
      method: "POST",
      body,
    }),

  listDocuments: (id: string) =>
    request<MandateDocumentDto[]>(`/mandates/${id}/documents`),

  uploadDocument: async (
    id: string,
    file: File,
    description?: string | null,
    documentType?: MandateDocumentTypeDto,
  ) => {
    const form = new FormData();
    form.set("file", file);
    if (description?.trim()) form.set("description", description.trim());
    if (documentType) form.set("documentType", documentType);
    const res = await authenticatedFetch(`/mandates/${id}/documents`, {
      method: "POST",
      headers: { Accept: "application/json" },
      body: form,
    });
    return (await res.json()) as MandateDocumentDto;
  },

  downloadDocument: async (mandateId: string, documentId: string) => {
    const res = await authenticatedFetch(
      `/mandates/${mandateId}/documents/${documentId}/download`,
    );
    return res.blob();
  },

  deleteDocument: (mandateId: string, documentId: string) =>
    request<void>(`/mandates/${mandateId}/documents/${documentId}`, {
      method: "DELETE",
    }),
};
