import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  DisputeEventCreateRequestDto,
  DisputeEventPatchRequestDto,
  DisputeEventResponseDto,
} from "@/types/api";

/**
 * Case-scoped dispute path events (kalima-backend {@code DisputeEventController}).
 * Write: ADMIN / SENIOR_LAWYER / LAWYER / SECRETARY. Read (+ CLIENT scope): also ACCOUNTANT / CLIENT.
 */
export const disputeEventsApi = {
  list: (
    caseId: string,
    q: { page?: number; size?: number } = {},
  ) =>
    request<PagedResponse<DisputeEventResponseDto>>(
      `/cases/${caseId}/dispute-events`,
      {
        query: {
          page: q.page ?? 0,
          size: q.size ?? 100,
        },
      },
    ),
  create: (caseId: string, body: DisputeEventCreateRequestDto) =>
    request<DisputeEventResponseDto>(
      `/cases/${caseId}/dispute-events`,
      { method: "POST", body },
    ),
  patch: (
    caseId: string,
    eventId: string,
    body: DisputeEventPatchRequestDto,
  ) =>
    request<DisputeEventResponseDto>(
      `/cases/${caseId}/dispute-events/${eventId}`,
      { method: "PATCH", body },
    ),
  /** 204 No Content — success returns {@code undefined}. */
  remove: (caseId: string, eventId: string) =>
    request<void>(`/cases/${caseId}/dispute-events/${eventId}`, {
      method: "DELETE",
    }),
};
