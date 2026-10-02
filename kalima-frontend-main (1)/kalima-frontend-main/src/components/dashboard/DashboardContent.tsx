"use client";

import { useEffect, useMemo, useState } from "react";

import {
  Briefcase,
  FileSearch,
  Gavel,
  GitBranch,
  ListChecks,
  Receipt,
} from "lucide-react";

import { DashboardActiveLitigation } from "@/components/dashboard/DashboardActiveLitigation";
import type { DashboardLitigationRow } from "@/components/dashboard/DashboardActiveLitigation";
import { DashboardActivityFeed } from "@/components/dashboard/DashboardActivityFeed";
import {
  DashboardHero,
  type DashboardHeroOpsMetrics,
} from "@/components/dashboard/DashboardHero";
import { DashboardCalendarToday } from "@/components/dashboard/DashboardCalendarToday";
import { MandateUpcomingWidget } from "@/components/dashboard/MandateUpcomingWidget";
import { DashboardTaskQueue } from "@/components/dashboard/DashboardTaskQueue";
import type { DashboardTaskFlat } from "@/components/dashboard/DashboardTaskQueue";
import { OperationalKpiCard } from "@/components/dashboard/OperationalKpiCard";
import { QuickActions } from "@/components/dashboard/QuickActions";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { DashboardPageSkeleton, PageLoading } from "@/components/shared/PageLoading";
import { casesApi } from "@/lib/api/cases";
import { ApiRequestError } from "@/lib/api/client";
import { documentsApi } from "@/lib/api/documents";
import { notificationsApi } from "@/lib/api/notifications";
import type { LitigationSampleRow } from "@/lib/dashboard/enrich-litigation-sample";
import { enrichLitigationSampleCases } from "@/lib/dashboard/enrich-litigation-sample";
import {
  fetchDashboardAuditPreview,
  fetchDashboardCriticalSnapshot,
} from "@/lib/dashboard/fetch-dashboard";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError } from "@/lib/feedback";
import { notificationToApp } from "@/lib/notifications/map-app-notification";
import type { TaskResponseDto } from "@/types/api";
import type { Hearing } from "@/types/hearing";
import type { CalendarEventDto } from "@/types/calendar";
import type { AuditLog } from "@/types/audit";
import type { AppNotification } from "@/types/notification";
import type { SystemRole } from "@/types/staff";

// -----------------------------------------------------------------------------
// Role → capability map (staff dashboard only — CLIENT uses /client/dashboard).
// -----------------------------------------------------------------------------
type Capabilities = {
  canSeeStats: boolean;
  canSeeOfficeHearings: boolean;
  canCreateCase: boolean;
  canSeeAudit: boolean;
  canSeeBilling: boolean;
  canSeeDocuments: boolean;
};

function capabilitiesFor(role: SystemRole | null): Capabilities {
  const r = role;
  return {
    canSeeStats:
      r === "ADMIN" ||
      r === "SENIOR_LAWYER" ||
      r === "LAWYER" ||
      r === "SECRETARY" ||
      r === "ACCOUNTANT",
    canSeeOfficeHearings:
      r === "ADMIN" ||
      r === "SENIOR_LAWYER" ||
      r === "LAWYER" ||
      r === "SECRETARY" ||
      r === "ACCOUNTANT",
    canCreateCase:
      r === "ADMIN" ||
      r === "SENIOR_LAWYER" ||
      r === "LAWYER" ||
      r === "SECRETARY",
    canSeeAudit: r === "ADMIN",
    canSeeBilling: r === "ADMIN" || r === "ACCOUNTANT",
    canSeeDocuments:
      r === "ADMIN" ||
      r === "SENIOR_LAWYER" ||
      r === "LAWYER" ||
      r === "SECRETARY" ||
      r === "ACCOUNTANT",
  };
}

const CASE_SAMPLE_SIZE = 10;

function flattenOperationalTasks(
  rows: (DashboardLitigationRow | LitigationSampleRow)[],
): DashboardTaskFlat[] {
  const out: DashboardTaskFlat[] = [];
  for (const r of rows) {
    for (const t of r.openTasks) {
      if (t.status === "DONE" || t.status === "CANCELLED") continue;
      out.push({
        ...t,
        caseNumber: r.case.caseNumber,
        caseTitle: r.case.title,
      });
    }
  }
  const todayKey = new Date().toISOString().slice(0, 10);
  const score = (x: DashboardTaskFlat) => {
    if (!x.dueDate) return 4;
    const d = x.dueDate.slice(0, 10);
    if (d < todayKey) return 0;
    if (d === todayKey) return 1;
    return 2;
  };
  const pri = (p: TaskResponseDto["priority"]) =>
    p === "URGENT" ? 0 : p === "HIGH" ? 1 : p === "MEDIUM" ? 2 : 3;
  out.sort((a, b) => {
    const sa = score(a);
    const sb = score(b);
    if (sa !== sb) return sa - sb;
    const pa = pri(a.priority);
    const pb = pri(b.priority);
    if (pa !== pb) return pa - pb;
    if (!a.dueDate) return 1;
    if (!b.dueDate) return -1;
    return a.dueDate < b.dueDate ? -1 : 1;
  });
  return out;
}

/** Defer non-critical widgets until after layout/paint. */
function scheduleIdle(cb: () => void): () => void {
  if (typeof window === "undefined") {
    const t = setTimeout(cb, 1);
    return () => clearTimeout(t);
  }
  const w = window;
  if (typeof w.requestIdleCallback === "function") {
    const id = w.requestIdleCallback(cb, { timeout: 750 });
    return () => {
      if (typeof w.cancelIdleCallback === "function") {
        w.cancelIdleCallback(id);
      } else {
        w.clearTimeout(id);
      }
    };
  }
  const t = w.setTimeout(cb, 48);
  return () => w.clearTimeout(t);
}

export function DashboardContent() {
  const [loadVersion, setLoadVersion] = useState(0);
  const [criticalBusy, setCriticalBusy] = useState(false);
  const [criticalReady, setCriticalReady] = useState(false);
  const [litigationSampleReady, setLitigationSampleReady] = useState(false);
  const [secondaryHydrated, setSecondaryHydrated] = useState(false);
  const [docsMetricHydrated, setDocsMetricHydrated] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [activeCases, setActiveCases] = useState(0);
  const [todayHearingsCount, setTodayHearingsCount] = useState(0);
  const [pendingTasks, setPendingTasks] = useState(0);
  const [overdueInvoice, setOverdueInvoice] = useState<number | null | undefined>(
    undefined,
  );
  const [calendarToday, setCalendarToday] = useState<CalendarEventDto[]>([]);
  const [calendarOverdueCount, setCalendarOverdueCount] = useState(0);
  const [litigationRows, setLitigationRows] = useState<DashboardLitigationRow[]>(
    [],
  );
  const [flatTasks, setFlatTasks] = useState<DashboardTaskFlat[]>([]);
  const [disputeStagesHot, setDisputeStagesHot] = useState(0);
  const [docsReviewCount, setDocsReviewCount] = useState<number | null>(null);

  const [notificationPreview, setNotificationPreview] = useState<
    AppNotification[]
  >([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [auditLoadFailed, setAuditLoadFailed] = useState(false);

  const [greeting, setGreeting] = useState("مرحباً، أستاذنا");
  const [role, setRole] = useState<SystemRole | null>(null);
  const [roleReady, setRoleReady] = useState(false);

  useEffect(() => {
    const u = getStoredUser();
    setRole((u?.systemRole as SystemRole | undefined) ?? null);
    setRoleReady(true);
    if (u?.fullName?.trim()) {
      setGreeting(`مرحباً، ${u.fullName.trim()}`);
    }
  }, []);

  const caps = useMemo<Capabilities>(() => capabilitiesFor(role), [role]);

  useEffect(() => {
    if (!roleReady) return;

    let cancelled = false;

    async function runCriticalPath() {
      setError(null);
      setCriticalReady(false);
      setCriticalBusy(true);
      setLitigationSampleReady(false);
      setSecondaryHydrated(false);
      setDocsMetricHydrated(!caps.canSeeDocuments);

      try {
        if (!caps.canSeeStats) {
          setActiveCases(0);
          setTodayHearingsCount(0);
          setPendingTasks(0);
          setOverdueInvoice(undefined);
          setCalendarToday([]);
          setCalendarOverdueCount(0);
          setLitigationRows([]);
          setFlatTasks([]);
          setDisputeStagesHot(0);
          setLitigationSampleReady(true);
          setAuditLogs([]);
          setAuditLoadFailed(false);
          setNotificationPreview([]);
          setCriticalBusy(false);
          setCriticalReady(true);
          return;
        }

        const [criticalSnapshot, casesPage] = await Promise.all([
          fetchDashboardCriticalSnapshot(),
          casesApi
            .list({
              page: 0,
              size: CASE_SAMPLE_SIZE,
              sort: "updatedAt,desc",
              includeArchived: false,
            })
            .catch(() => null),
        ]);

        if (cancelled) return;

        setActiveCases(criticalSnapshot.activeCases);
        setTodayHearingsCount(criticalSnapshot.todayHearingsCount);
        setPendingTasks(criticalSnapshot.pendingTasks);
        setOverdueInvoice(criticalSnapshot.overdueInvoiceCount);
        setCalendarToday(criticalSnapshot.calendarToday);
        setCalendarOverdueCount(criticalSnapshot.calendarOverdueCount);

        setCriticalReady(true);

        const hearingByCaseId = new Map<string, Hearing>();
        for (const h of criticalSnapshot.todayHearingsPreview) {
          if (!hearingByCaseId.has(h.caseId)) hearingByCaseId.set(h.caseId, h);
        }

        const casesArr = casesPage?.content ?? [];
        if (casesArr.length === 0) {
          setLitigationRows([]);
          setFlatTasks([]);
          setDisputeStagesHot(0);
          setLitigationSampleReady(true);
          setCriticalBusy(false);
          return;
        }

        enrichLitigationSampleCases(casesArr, hearingByCaseId, {
          concurrency: 4,
        })
          .then((result) => {
            if (cancelled) return;
            setLitigationRows(result.rows as DashboardLitigationRow[]);
            setFlatTasks(flattenOperationalTasks(result.rows));
            setDisputeStagesHot(result.disputeStagesHot);
          })
          .catch(() => {
            if (cancelled) return;
            setLitigationRows([]);
            setFlatTasks([]);
            setDisputeStagesHot(0);
          })
          .finally(() => {
            if (!cancelled) {
              setLitigationSampleReady(true);
              setCriticalBusy(false);
            }
          });
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل لوحة التحكم");
        }
        setLitigationRows([]);
        setFlatTasks([]);
        setDisputeStagesHot(0);
        setLitigationSampleReady(true);
        setCriticalBusy(false);
        setCriticalReady(false);
      }
    }

    void runCriticalPath();

    return () => {
      cancelled = true;
    };
  }, [
    loadVersion,
    roleReady,
    caps.canSeeStats,
    caps.canSeeOfficeHearings,
    caps.canSeeDocuments,
  ]);

  useEffect(() => {
    if (!criticalReady || !roleReady) return;

    let cancelled = false;

    const cancelScheduled = scheduleIdle(() => {
      void (async () => {
        try {
          if (cancelled) return;

          try {
            const notifPage = await notificationsApi
              .list({ page: 0, size: 12 })
              .catch(() => null);
            if (cancelled) return;
            setNotificationPreview(
              notifPage ?
                notifPage.content.map((n) => notificationToApp(n))
              : [],
            );
          } catch {
            if (!cancelled) setNotificationPreview([]);
          }

          await new Promise((r) => setTimeout(r, 48));
          if (cancelled) return;

          const auditPromise =
            caps.canSeeAudit ?
              fetchDashboardAuditPreview()
            : Promise.resolve({
                auditPreview: [] as AuditLog[],
                auditLoadFailed: false,
              });

          const docsPromise =
            caps.canSeeStats && caps.canSeeDocuments ?
              documentsApi.list({ status: "UNDER_REVIEW" }).catch(() => null)
            : Promise.resolve(null);

          const [{ auditPreview, auditLoadFailed: auditFail }, docsUnderReview] =
            await Promise.all([auditPromise, docsPromise]);

          if (cancelled) return;
          setAuditLogs(auditPreview);
          setAuditLoadFailed(auditFail);

          if (caps.canSeeDocuments && Array.isArray(docsUnderReview)) {
            setDocsReviewCount(docsUnderReview.length);
          } else {
            setDocsReviewCount(null);
          }

          setDocsMetricHydrated(true);
          setSecondaryHydrated(true);
        } catch {
          if (!cancelled) {
            setDocsMetricHydrated(true);
            setSecondaryHydrated(true);
          }
        }
      })();
    });

    return () => {
      cancelled = true;
      cancelScheduled();
    };
  }, [
    criticalReady,
    roleReady,
    caps.canSeeAudit,
    caps.canSeeDocuments,
    caps.canSeeStats,
  ]);

  const heroOps: DashboardHeroOpsMetrics | null =
    caps.canSeeStats ?
      {
        activeCases,
        hearingsToday: todayHearingsCount,
        pendingTasks,
        disputePathsHot: disputeStagesHot,
      }
    : null;

  async function handlePreviewMarkRead(id: string) {
    try {
      await notificationsApi.markRead(id);
      setNotificationPreview((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n)),
      );
    } catch (e) {
      notifyApiError(e, "تعذر تحديث حالة القراءة");
    }
  }

  async function handlePreviewReadAll() {
    try {
      await notificationsApi.readAll();
      setNotificationPreview((prev) => prev.map((n) => ({ ...n, read: true })));
    } catch (e) {
      notifyApiError(e, "تعذر تعيين الكل كمقروء");
    }
  }

  const showInitialSkeleton =
    (!roleReady || (caps.canSeeStats && criticalBusy && !criticalReady)) &&
    !error;

  let kpiDelay = 0;
  const bump = () => {
    const d = kpiDelay;
    kpiDelay += 0.045;
    return d;
  };

  const disputeHotPending = caps.canSeeStats && !litigationSampleReady;
  const timelineLoading = criticalReady && !secondaryHydrated;

  return (
    <div className="space-y-5">
      {error ?
        <ApiErrorAlert
          message={error}
          title="تعذر تحميل لوحة التحكم"
          onRetry={() => setLoadVersion((v) => v + 1)}
        />
      : null}

      {showInitialSkeleton ?
        <DashboardPageSkeleton />
      : (
        <>
          <DashboardHero
            greeting={greeting}
            canCreateCase={caps.canCreateCase}
            ops={heroOps}
            disputeHotPending={disputeHotPending}
          />

          {caps.canSeeStats ?
            <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">              <OperationalKpiCard
                label="القضايا النشطة"
                value={activeCases}
                hint="من ملخص التشغيل — غير المؤرشفة"
                icon={<Briefcase />}
                accent="navy"
                delay={bump()}
              />
              <OperationalKpiCard
                label="مواعيد اليوم"
                value={todayHearingsCount}
                hint="من التقويم المركزي"
                icon={<Gavel />}
                accent="gold"
                delay={bump()}
              />
              <OperationalKpiCard
                label="المهام الحرجة"
                value={pendingTasks}
                hint="غير المنجزة في نطاق الموجز"
                icon={<ListChecks />}
                accent="success"
                delay={bump()}
              />
              <OperationalKpiCard
                label="مسارات بمرحلة جارية"
                value={disputeStagesHot}
                hint={`من أحدث ${CASE_SAMPLE_SIZE} ملفات`}
                icon={<GitBranch />}
                accent="info"
                delay={bump()}
                valueLoading={disputeHotPending}
              />
              <OperationalKpiCard
                label="مواعيد متأخرة"
                value={calendarOverdueCount}
                hint="من التقويم المركزي"
                icon={<Gavel />}
                accent="danger"
                delay={bump()}
              />
              {caps.canSeeDocuments ?
                <OperationalKpiCard
                  label="مستندات قيد المراجعة"
                  value={docsReviewCount === null ? "—" : docsReviewCount}
                  hint={
                    docsReviewCount === null ?
                      "تعذر تحميل العدّ أو لا صلاحية عرض الحالة."
                    : "حالة UNDER_REVIEW من واجهة المستندات"
                  }
                  icon={<FileSearch />}
                  accent="navy"
                  delay={bump()}
                  valueLoading={!docsMetricHydrated}
                />
              : null}
              {caps.canSeeBilling ?
                <OperationalKpiCard
                  label="الفواتير المتأخرة"
                  value={
                    overdueInvoice === null || overdueInvoice === undefined ?
                      "—"
                    : overdueInvoice
                  }
                  hint={
                    overdueInvoice === null ?
                      "الحقل غير مفعّل في الخلفية بعد"
                    : "بحسب تقرير الفوترة إن توفّر"
                  }
                  icon={<Receipt />}
                  accent="danger"
                  delay={bump()}
                />
              : null}
            </section>
          : null}

          <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_22rem] xl:gap-7 2xl:grid-cols-[minmax(0,1fr)_24rem]">
            <div className="flex min-w-0 flex-col gap-5">
              {caps.canSeeStats ?
                litigationSampleReady ?
                  <DashboardActiveLitigation rows={litigationRows} />
                : <PageLoading label="جارٍ تحميل عيّنة القضايا…" minHeightClassName="min-h-[14rem]" />
              : null}

              {caps.canSeeOfficeHearings ?
                <DashboardCalendarToday
                  events={calendarToday}
                  reportedTodayCount={todayHearingsCount}
                />
              : null}

              {caps.canSeeStats ?
                litigationSampleReady ?
                  <DashboardTaskQueue tasks={flatTasks} />
                : <PageLoading label="جارٍ تجميع مهام العيّنة…" minHeightClassName="min-h-[11rem]" />
              : null}

              {caps.canSeeStats ? <MandateUpcomingWidget /> : null}

              <QuickActions />
            </div>

            <aside className="min-w-0 xl:sticky xl:top-24 xl:self-start">
              <DashboardActivityFeed
                notifications={notificationPreview}
                auditLogs={auditLoadFailed ? [] : auditLogs}
                canSeeAudit={caps.canSeeAudit && !auditLoadFailed}
                onMarkRead={handlePreviewMarkRead}
                onReadAll={
                  notificationPreview.some((n) => !n.read) ?
                    handlePreviewReadAll
                  : undefined
                }
                timelineLoading={timelineLoading}
              />
            </aside>
          </div>
        </>
      )}
    </div>
  );
}
