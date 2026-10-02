import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  DelegationActivityDto,
  DelegationCaseLinkDto,
  DelegationCreateRequestDto,
  DelegationListItemDto,
  DelegationResponseDto,
  DelegationStatusDto,
  DelegationTypeDto,
  DelegationUpdateRequestDto,
} from "@/types/delegation";

export type DelegationsListQuery = {
  page?: number;
  size?: number;
  status?: DelegationStatusDto;
  type?: DelegationTypeDto;
  clientId?: string;
  agentId?: string;
  issuingCountry?: string;
  starredOnly?: boolean;
  search?: string;
};

function exportQuery(q: Omit<DelegationsListQuery, "page" | "size">) {
  const params = new URLSearchParams();
  if (q.status) params.set("status", q.status);
  if (q.type) params.set("type", q.type);
  if (q.clientId) params.set("clientId", q.clientId);
  if (q.agentId) params.set("agentId", q.agentId);
  if (q.issuingCountry) params.set("issuingCountry", q.issuingCountry);
  if (q.starredOnly) params.set("starredOnly", "true");
  if (q.search?.trim()) params.set("search", q.search.trim());
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export const delegationsApi = {
  list: (q: DelegationsListQuery = {}) =>
    request<PagedResponse<DelegationListItemDto>>("/delegations", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.status ? { status: q.status } : {}),
        ...(q.type ? { type: q.type } : {}),
        ...(q.clientId ? { clientId: q.clientId } : {}),
        ...(q.agentId ? { agentId: q.agentId } : {}),
        ...(q.issuingCountry ? { issuingCountry: q.issuingCountry } : {}),
        ...(q.starredOnly ? { starredOnly: true } : {}),
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
      },
    }),

  get: (id: string) => request<DelegationResponseDto>(`/delegations/${id}`),

  create: (body: DelegationCreateRequestDto) =>
    request<DelegationResponseDto>("/delegations", { method: "POST", body }),

  patch: (id: string, body: DelegationUpdateRequestDto) =>
    request<DelegationResponseDto>(`/delegations/${id}`, { method: "PATCH", body }),

  exportCsv: async (q: Omit<DelegationsListQuery, "page" | "size"> = {}) => {
    const res = await authenticatedFetch(`/delegations/export${exportQuery(q)}`);
    return res.blob();
  },

  listCases: (id: string) =>
    request<DelegationCaseLinkDto[]>(`/delegations/${id}/cases`),

  linkCase: (id: string, caseId: string) =>
    request<DelegationCaseLinkDto>(`/delegations/${id}/cases`, {
      method: "POST",
      body: { caseId },
    }),

  unlinkCase: (id: string, caseId: string) =>
    request<void>(`/delegations/${id}/cases/${caseId}`, { method: "DELETE" }),

  activity: (id: string, page = 0, size = 20) =>
    request<PagedResponse<DelegationActivityDto>>(`/delegations/${id}/activity`, {
      query: { page, size },
    }),
};
