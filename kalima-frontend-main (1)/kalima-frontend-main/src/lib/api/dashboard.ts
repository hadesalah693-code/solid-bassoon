import { request } from "./client";

/** Matches `DashboardSummaryResponse` from kalima-backend. */
export type DashboardSummaryDto = {
  activeCaseCount: number;
  hearingsTodayCount: number;
  pendingTaskCount: number;
  overdueInvoiceCount: number | null;
};

export const dashboardApi = {
  summary: () => request<DashboardSummaryDto>("/dashboard/summary"),
};
