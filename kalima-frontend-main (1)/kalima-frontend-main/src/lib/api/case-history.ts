import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type { CaseHistoryEntry } from "@/types/case-history";

/**
 * Client for `GET /api/cases/{id}/history`.
 *
 * The backend endpoint reuses the same access gate as `GET /api/cases/{id}`:
 * every role that can read a case can read its history. Tenant isolation
 * and CLIENT-ownership are enforced server-side; this module does not
 * need to filter.
 */
export const caseHistoryApi = {
  /**
   * Returns the newest entries first. The backend default page size is 50,
   * which is enough for most cases' lifetimes without paging; callers can
   * override it for deeper inspection.
   */
  list: (
    caseId: string,
    q: { page?: number; size?: number } = {},
  ): Promise<PagedResponse<CaseHistoryEntry>> =>
    request<PagedResponse<CaseHistoryEntry>>(`/cases/${caseId}/history`, {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 50,
      },
    }),
};
