import { calendarApi } from "@/lib/api/calendar";
import { auditApi } from "@/lib/api/audit";
import { dashboardApi } from "@/lib/api/dashboard";
import type { CalendarEventDto } from "@/types/calendar";
import type { AuditLog } from "@/types/audit";
import type { Hearing } from "@/types/hearing";

/** Counts + quick hearing peek from `/dashboard/summary` and calendar APIs. */
export type DashboardCriticalSnapshot = {
  activeCases: number;
  todayHearingsCount: number;
  pendingTasks: number;
  /** Server returns null when invoices are not implemented. */
  overdueInvoiceCount: number | null;
  todayHearingsPreview: Hearing[];
  /** Calendar hub — preferred for date widgets. */
  calendarToday: CalendarEventDto[];
  calendarTodayCount: number;
  calendarOverdueCount: number;
};

/** Full aggregate including audit (legacy single-shot loader). */
export type DashboardSnapshot = DashboardCriticalSnapshot & {
  auditPreview: AuditLog[];
  auditLoadFailed: boolean;
};

/**
 * Loads summary counts plus an optional today's-hearing snippet.
 * Intended for first paint — does not call audit/notifications/documents APIs.
 */
export async function fetchDashboardCriticalSnapshot(): Promise<DashboardCriticalSnapshot> {
  const summary = await dashboardApi.summary();

  let calendarToday: CalendarEventDto[] = [];
  let calendarTodayCount = summary.hearingsTodayCount;
  let calendarOverdueCount = 0;

  try {
    const [todayEvents, calSummary] = await Promise.all([
      calendarApi.today(),
      calendarApi.summary(),
    ]);
    calendarToday = todayEvents;
    calendarTodayCount = calSummary.todayCount;
    calendarOverdueCount = calSummary.overdueCount;
  } catch {
    calendarToday = [];
  }

  return {
    activeCases: summary.activeCaseCount,
    todayHearingsCount: calendarTodayCount,
    pendingTasks: summary.pendingTaskCount,
    overdueInvoiceCount: summary.overdueInvoiceCount,
    todayHearingsPreview: [],
    calendarToday,
    calendarTodayCount,
    calendarOverdueCount,
  };
}

/**
 * Admin-only audit teaser for the activity column (defer after critical path).
 */
export async function fetchDashboardAuditPreview(): Promise<{
  auditPreview: AuditLog[];
  auditLoadFailed: boolean;
}> {
  let auditPreview: AuditLog[] = [];
  let auditLoadFailed = false;
  try {
    const a = await auditApi.list({ page: 0, size: 5 });
    auditPreview = a.content;
  } catch {
    auditPreview = [];
    auditLoadFailed = true;
  }
  return { auditPreview, auditLoadFailed };
}

/**
 * Legacy single call: summary + hearings + audit.
 * Prefer {@link fetchDashboardCriticalSnapshot} + deferred {@link fetchDashboardAuditPreview} on the dashboard.
 */
export async function fetchDashboardSnapshot(options: {
  includeAudit: boolean;
  includeOfficeHearings?: boolean;
}): Promise<DashboardSnapshot> {
  const critical = await fetchDashboardCriticalSnapshot();
  let auditPreview: AuditLog[] = [];
  let auditLoadFailed = false;
  if (options.includeAudit) {
    const a = await fetchDashboardAuditPreview();
    auditPreview = a.auditPreview;
    auditLoadFailed = a.auditLoadFailed;
  }

  return {
    ...critical,
    auditPreview,
    auditLoadFailed,
  };
}
