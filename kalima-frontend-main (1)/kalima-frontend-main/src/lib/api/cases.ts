import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  CaseCreateRequestDto,
  CaseResponseDto,
  CaseUpdateRequestDto,
  CaseInternalNoteCreateDto,
  CaseInternalNoteDto,
} from "@/types/api";
import type { CasePriority, CaseStatus } from "@/types/case";

type CaseListQuery = {
  page?: number;
  size?: number;
  search?: string;
  includeArchived?: boolean;
  status?: string;
  /** When set, lists cases for this client person id (server also enforces for CLIENT role). */
  clientId?: string;
  assignedLawyerId?: string;
  priority?: CasePriority;
  fromDate?: string;
  toDate?: string;
  /** Spring Data sort, e.g. `updatedAt,desc` */
  sort?: string;
};

export const casesApi = {
  list: (q: CaseListQuery = {}) =>
    request<PagedResponse<CaseResponseDto>>("/cases", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.search ? { search: q.search } : {}),
        ...(q.includeArchived != null
          ? { includeArchived: q.includeArchived }
          : {}),
        ...(q.status ? { status: q.status } : {}),
        ...(q.clientId ? { clientId: q.clientId } : {}),
        ...(q.assignedLawyerId ? { assignedLawyerId: q.assignedLawyerId } : {}),
        ...(q.priority ? { priority: q.priority } : {}),
        ...(q.fromDate ? { fromDate: q.fromDate } : {}),
        ...(q.toDate ? { toDate: q.toDate } : {}),
        ...(q.sort ? { sort: q.sort } : {}),
      },
    }),
  get: (id: string) => request<CaseResponseDto>(`/cases/${id}`),
  relatedCases: (id: string, page = 0, size = 20) =>
    request<PagedResponse<CaseResponseDto>>(`/cases/${id}/related-cases`, {
      query: { page, size },
    }),
  listInternalNotes: (id: string, page = 0, size = 50) =>
    request<PagedResponse<CaseInternalNoteDto>>(`/cases/${id}/internal-notes`, {
      query: { page, size },
    }),
  addInternalNote: (id: string, body: CaseInternalNoteCreateDto) =>
    request<CaseInternalNoteDto>(`/cases/${id}/internal-notes`, {
      method: "POST",
      body,
    }),
  create: (body: CaseCreateRequestDto) =>
    request<CaseResponseDto>("/cases", { method: "POST", body }),
  update: (id: string, body: CaseUpdateRequestDto) =>
    request<CaseResponseDto>(`/cases/${id}`, { method: "PUT", body }),
  /** PATCH /api/cases/{id}/status — [ADMIN, SENIOR_LAWYER, LAWYER]. */
  changeStatus: (id: string, status: CaseStatus) =>
    request<CaseResponseDto>(`/cases/${id}/status`, {
      method: "PATCH",
      body: { status },
    }),
  /** DELETE /api/cases/{id} — soft-archive. [ADMIN, SENIOR_LAWYER]. 204 No Content. */
  archive: (id: string) =>
    request<void>(`/cases/${id}`, { method: "DELETE" }),
  /** POST /api/cases/{id}/restore — [ADMIN, SENIOR_LAWYER]. */
  restore: (id: string) =>
    request<CaseResponseDto>(`/cases/${id}/restore`, { method: "POST" }),
};
