import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  HearingCreateRequestDto,
  HearingOfficeItemDto,
  HearingResponseDto,
  HearingUpdateRequestDto,
  HearingStatusUpdateRequestDto,
  HearingStatusDto,
  HearingTypeDto,
} from "@/types/api";

function localDateParam(d: Date) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export const hearingsApi = {
  create: (caseId: string, body: HearingCreateRequestDto) =>
    request<HearingResponseDto>(`/cases/${caseId}/hearings`, {
      method: "POST",
      body,
    }),
  /** GET /api/cases/{caseId}/hearings/{hearingId}. */
  get: (caseId: string, hearingId: string) =>
    request<HearingResponseDto>(
      `/cases/${caseId}/hearings/${hearingId}`,
    ),
  /** PUT /api/cases/{caseId}/hearings/{hearingId} — full replacement. */
  update: (
    caseId: string,
    hearingId: string,
    body: HearingUpdateRequestDto,
  ) =>
    request<HearingResponseDto>(
      `/cases/${caseId}/hearings/${hearingId}`,
      { method: "PUT", body },
    ),
  updateStatus: (
    caseId: string,
    hearingId: string,
    body: HearingStatusUpdateRequestDto,
  ) =>
    request<HearingResponseDto>(
      `/cases/${caseId}/hearings/${hearingId}/status`,
      { method: "PATCH", body },
    ),
  delete: (caseId: string, hearingId: string) =>
    request<void>(`/cases/${caseId}/hearings/${hearingId}`, {
      method: "DELETE",
    }),
  listByCase: (
    caseId: string,
    q: {
      page?: number;
      size?: number;
      from?: Date;
      to?: Date;
      status?: HearingStatusDto;
      type?: HearingTypeDto;
      lawyerId?: string;
    } = {},
  ) =>
    request<PagedResponse<HearingResponseDto>>(
      `/cases/${caseId}/hearings`,
      {
        query: {
          page: q.page ?? 0,
          size: q.size ?? 50,
          ...(q.from ? { from: localDateParam(q.from) } : {}),
          ...(q.to ? { to: localDateParam(q.to) } : {}),
          ...(q.status ? { status: q.status } : {}),
          ...(q.type ? { type: q.type } : {}),
          ...(q.lawyerId ? { lawyerId: q.lawyerId } : {}),
        },
      },
    ),

  /** All hearings in the tenant for a calendar day (default: today on server if omitted). */
  listOffice: (q: {
    date?: Date;
    from?: Date;
    to?: Date;
    status?: HearingStatusDto;
    type?: HearingTypeDto;
    lawyerId?: string;
    page?: number;
    size?: number;
  } = {}) =>
    request<PagedResponse<HearingOfficeItemDto>>("/hearings", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.date ? { date: localDateParam(q.date) } : {}),
        ...(q.from ? { from: localDateParam(q.from) } : {}),
        ...(q.to ? { to: localDateParam(q.to) } : {}),
        ...(q.status ? { status: q.status } : {}),
        ...(q.type ? { type: q.type } : {}),
        ...(q.lawyerId ? { lawyerId: q.lawyerId } : {}),
      },
    }),
};
