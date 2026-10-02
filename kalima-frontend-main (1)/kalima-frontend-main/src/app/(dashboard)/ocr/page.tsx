"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  Clock,
  FileScan,
  History,
  LayoutDashboard,
  Plus,
  RefreshCw,
  TrendingUp,
  Users,
} from "lucide-react";

import { useAuth } from "@/components/auth/auth-provider";
import { OperationalKpiCard } from "@/components/dashboard/OperationalKpiCard";
import {
  EMPTY_OCR_FILTERS,
  OcrSearchFilters,
  type OcrFilterState,
} from "@/components/ocr/OcrSearchFilters";
import { OcrRecordsTable } from "@/components/ocr/OcrRecordsTable";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { Pagination } from "@/components/shared/Pagination";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { ApiRequestError } from "@/lib/api/client";
import { OCR_PAGE_SIZE, ocrApi } from "@/lib/api/ocr";
import { cn } from "@/lib/utils/cn";
import type { OcrRecordListDto, OcrStatsDto } from "@/types/ocr";
import type { SystemRole } from "@/types/staff";

type Tab = "dashboard" | "history";

function canUpload(role: SystemRole | null | undefined) {
  return role === "ADMIN" || role === "SENIOR_LAWYER" || role === "LAWYER";
}

export default function OcrModulePage() {
  const { user } = useAuth();
  const role = user?.systemRole as SystemRole | undefined;
  const [tab, setTab] = useState<Tab>("dashboard");
  const [stats, setStats] = useState<OcrStatsDto | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState<string | null>(null);
  const [recent, setRecent] = useState<OcrRecordListDto[]>([]);
  const [processing, setProcessing] = useState<OcrRecordListDto[]>([]);
  const [failed, setFailed] = useState<OcrRecordListDto[]>([]);
  const [dashLoading, setDashLoading] = useState(true);
  const [dashError, setDashError] = useState<string | null>(null);
  const [filters, setFilters] = useState<OcrFilterState>(EMPTY_OCR_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<OcrFilterState>(
    EMPTY_OCR_FILTERS,
  );
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [historyItems, setHistoryItems] = useState<OcrRecordListDto[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    setStatsError(null);
    try {
      setStats(await ocrApi.stats());
    } catch (e) {
      setStatsError(
        e instanceof ApiRequestError ? e.message : "تعذّر تحميل الإحصائيات",
      );
    } finally {
      setStatsLoading(false);
    }
  }, []);

  const loadDashboardLists = useCallback(async () => {
    setDashLoading(true);
    setDashError(null);
    try {
      const [recentPage, processingPage, pendingPage, failedPage] =
        await Promise.all([
          ocrApi.search({ page: 0, size: 5, sort: "createdAt,desc" }),
          ocrApi.search({
            status: "PROCESSING",
            page: 0,
            size: 10,
            sort: "createdAt,asc",
          }),
          ocrApi.search({
            status: "PENDING",
            page: 0,
            size: 10,
            sort: "createdAt,asc",
          }),
          ocrApi.search({
            status: "FAILED",
            page: 0,
            size: 10,
            sort: "updatedAt,desc",
          }),
        ]);
      setRecent(recentPage.content);
      setProcessing([...processingPage.content, ...pendingPage.content]);
      setFailed(failedPage.content);
    } catch (e) {
      setDashError(
        e instanceof ApiRequestError ? e.message : "تعذّر تحميل لوحة OCR",
      );
    } finally {
      setDashLoading(false);
    }
  }, []);

  const loadHistory = useCallback(async () => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const result = await ocrApi.search({
        ...appliedFilters,
        page,
        size: OCR_PAGE_SIZE,
        sort: "createdAt,desc",
      });
      setHistoryItems(result.content);
      setTotal(result.totalElements);
    } catch (e) {
      setHistoryError(
        e instanceof ApiRequestError ? e.message : "تعذّر تحميل السجل",
      );
      setHistoryItems([]);
      setTotal(0);
    } finally {
      setHistoryLoading(false);
    }
  }, [appliedFilters, page]);

  useEffect(() => {
    void loadStats();
    void loadDashboardLists();
  }, [loadStats, loadDashboardLists, refreshKey]);

  useEffect(() => {
    if (tab === "history") void loadHistory();
  }, [tab, loadHistory, refreshKey]);

  function refreshAll() {
    setRefreshKey((k) => k + 1);
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="التعرف الضوئي OCR"
        description="رفع المستندات، استخراج النص العربي والإنجليزي، والبحث في الأرشيف."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" size="sm" onClick={refreshAll}>
              <RefreshCw className="size-4" aria-hidden />
              تحديث
            </Button>
            {canUpload(role) ? (
              <Button asChild size="sm">
                <Link href="/ocr/new">
                  <Plus className="size-4" aria-hidden />
                  رفع مستند
                </Link>
              </Button>
            ) : null}
          </div>
        }
      />

      <div className="flex flex-wrap gap-2 border-b border-[var(--color-border)] pb-1">
        <TabButton
          active={tab === "dashboard"}
          onClick={() => setTab("dashboard")}
          icon={LayoutDashboard}
          label="لوحة التحكم"
        />
        <TabButton
          active={tab === "history"}
          onClick={() => setTab("history")}
          icon={History}
          label="السجل"
        />
      </div>

      {tab === "dashboard" ? (
        <>
          {statsError ? (
            <ApiErrorAlert message={statsError} onRetry={() => void loadStats()} />
          ) : null}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5">
            <OperationalKpiCard
              label="إجمالي المستندات"
              value={stats?.totalDocuments ?? 0}
              valueLoading={statsLoading}
              icon={<FileScan aria-hidden />}
              accent="navy"
            />
            <OperationalKpiCard
              label="معالَج اليوم"
              value={stats?.processedToday ?? 0}
              valueLoading={statsLoading}
              icon={<Clock aria-hidden />}
              accent="gold"
              delay={0.05}
            />
            <OperationalKpiCard
              label="قيد المعالجة"
              value={stats?.processing ?? 0}
              valueLoading={statsLoading}
              icon={<RefreshCw aria-hidden />}
              accent="info"
              delay={0.1}
            />
            <OperationalKpiCard
              label="فشل"
              value={stats?.failed ?? 0}
              valueLoading={statsLoading}
              icon={<AlertTriangle aria-hidden />}
              accent="danger"
              delay={0.15}
            />
            <OperationalKpiCard
              label="نسبة النجاح (30 يوم)"
              value={
                stats ? `${Math.round(stats.successRatePercent)}%` : "—"
              }
              valueLoading={statsLoading}
              icon={<TrendingUp aria-hidden />}
              accent="success"
              delay={0.2}
            />
          </div>

          {stats?.mostActiveUsers?.length ? (
            <SectionCard title="أكثر المستخدمين نشاطاً">
              <ul className="divide-y divide-[var(--color-border)]">
                {stats.mostActiveUsers.map((u) => (
                  <li
                    key={u.userId}
                    className="flex items-center justify-between gap-3 py-2.5 text-sm"
                  >
                    <span className="flex items-center gap-2">
                      <Users className="size-4 text-[var(--color-muted-foreground)]" />
                      {u.fullName ?? u.userId.slice(0, 8)}
                    </span>
                    <span className="tabular-nums text-[var(--color-muted-foreground)] ltr-nums">
                      {u.uploadCount} رفع
                    </span>
                  </li>
                ))}
              </ul>
            </SectionCard>
          ) : null}

          {dashError ? (
            <ApiErrorAlert
              message={dashError}
              onRetry={() => void loadDashboardLists()}
            />
          ) : dashLoading ? (
            <PageLoading label="جارٍ تحميل لوحة OCR…" minHeightClassName="min-h-[12rem]" />
          ) : (
            <div className="grid gap-6 xl:grid-cols-2">
              <SectionCard title="أحدث المستندات">
                <OcrRecordsTable items={recent} compact emptyMessage="لا مستندات بعد." />
              </SectionCard>
              <SectionCard title="قائمة المعالجة">
                <OcrRecordsTable
                  items={processing}
                  compact
                  emptyMessage="لا مهام معلّقة."
                />
              </SectionCard>
              <SectionCard title="مهام فاشلة" className="xl:col-span-2">
                <OcrRecordsTable
                  items={failed}
                  emptyMessage="لا سجلات فاشلة."
                />
              </SectionCard>
            </div>
          )}
        </>
      ) : (
        <>
          <OcrSearchFilters
            filters={filters}
            onChange={setFilters}
            onApply={() => {
              setPage(0);
              setAppliedFilters(filters);
              setRefreshKey((k) => k + 1);
            }}
            onReset={() => {
              setFilters(EMPTY_OCR_FILTERS);
              setAppliedFilters(EMPTY_OCR_FILTERS);
              setPage(0);
              setRefreshKey((k) => k + 1);
            }}
            showAdvanced={showAdvanced}
            onToggleAdvanced={() => setShowAdvanced((v) => !v)}
          />

          {historyError ? (
            <ApiErrorAlert
              message={historyError}
              onRetry={() => void loadHistory()}
            />
          ) : historyLoading ? (
            <PageLoading label="جارٍ تحميل السجل…" />
          ) : historyItems.length === 0 ? (
            <EmptyState
              title="لا نتائج"
              description="جرّب تعديل معايير البحث أو ارفع مستنداً جديداً."
              icon={FileScan}
            />
          ) : (
            <>
              <OcrRecordsTable items={historyItems} />
              <Pagination
                page={page}
                pageSize={OCR_PAGE_SIZE}
                total={total}
                onPageChange={setPage}
              />
            </>
          )}
        </>
      )}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  icon: Icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}) {
  return (
    <button
      type="button"
      role="tab"
      onClick={onClick}
      aria-selected={active}
      className={cn(
        "inline-flex items-center gap-2 border-b-2 px-3 py-2 text-sm font-medium transition-colors",
        active
          ? "border-[var(--color-gold-500)] text-[var(--color-foreground)]"
          : "border-transparent text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
      )}
    >
      <Icon className="size-4" aria-hidden />
      {label}
    </button>
  );
}
