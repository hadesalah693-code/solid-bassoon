"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Plus, RotateCcw, ShieldAlert } from "lucide-react";
import { useSearchParams } from "next/navigation";

import { CriminalReportStatusFilter } from "@/components/criminal-reports/CriminalReportStatusFilter";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { OperationalKpiCard } from "@/components/dashboard/OperationalKpiCard";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { Pagination } from "@/components/shared/Pagination";
import { SearchInput } from "@/components/shared/SearchInput";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { criminalReportsApi } from "@/lib/api/criminal-reports";
import { ApiRequestError } from "@/lib/api/client";
import {
  CRIMINAL_REPORT_STATUS_AR,
  CRIMINAL_REPORT_STATUS_TONE,
} from "@/lib/criminal-reports/labels";
import { useDebounce } from "@/hooks/use-debounce";
import { formatDate } from "@/lib/utils/format-date";
import type { CriminalReportListItemDto, CriminalReportStatusDto } from "@/types/criminal-report";

const PAGE_SIZE = 20;

export default function CriminalReportsPage() {
  return (
    <Suspense fallback={<PageLoading label="جاري تحميل البلاغات..." />}>
      <CriminalReportsPageInner />
    </Suspense>
  );
}

function CriminalReportsPageInner() {
  const searchParams = useSearchParams();
  const initialSearch = searchParams.get("search") ?? "";

  const [rows, setRows] = useState<CriminalReportListItemDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [statsLoading, setStatsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState(initialSearch);
  const debouncedSearch = useDebounce(search, 350);
  const [status, setStatus] = useState<CriminalReportStatusDto | "">("");
  const [fetchId, setFetchId] = useState(0);
  const [statusCounts, setStatusCounts] = useState<
    Record<CriminalReportStatusDto | "all", number>
  >({
    all: 0,
    UNDER_INVESTIGATION: 0,
    REFERRAL: 0,
    ARCHIVED: 0,
    FINAL_ARCHIVED: 0,
  });

  const hasActiveFilters = !!status || !!search.trim();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setStatsLoading(true);
      try {
        const [allRes, investigationRes, referralRes, archivedRes, finalRes] = await Promise.all([
          criminalReportsApi.list({ page: 0, size: 1 }),
          criminalReportsApi.list({ page: 0, size: 1, status: "UNDER_INVESTIGATION" }),
          criminalReportsApi.list({ page: 0, size: 1, status: "REFERRAL" }),
          criminalReportsApi.list({ page: 0, size: 1, status: "ARCHIVED" }),
          criminalReportsApi.list({ page: 0, size: 1, status: "FINAL_ARCHIVED" }),
        ]);
        if (!cancelled) {
          setStatusCounts({
            all: allRes.totalElements,
            UNDER_INVESTIGATION: investigationRes.totalElements,
            REFERRAL: referralRes.totalElements,
            ARCHIVED: archivedRes.totalElements,
            FINAL_ARCHIVED: finalRes.totalElements,
          });
        }
      } catch {
        if (!cancelled) {
          setStatusCounts({
            all: 0,
            UNDER_INVESTIGATION: 0,
            REFERRAL: 0,
            ARCHIVED: 0,
            FINAL_ARCHIVED: 0,
          });
        }
      } finally {
        if (!cancelled) setStatsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [fetchId]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await criminalReportsApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          ...(status ? { status } : {}),
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل البلاغات");
          setRows([]);
          setTotal(0);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, debouncedSearch, status, fetchId]);

  const filteredLabel = useMemo(() => {
    if (!status) return "البلاغات";
    return CRIMINAL_REPORT_STATUS_AR[status];
  }, [status]);

  function clearFilters() {
    setStatus("");
    setSearch("");
    setPage(0);
  }

  return (
    <div dir="rtl" className="space-y-6">
      <DisputeModuleHero
        icon={ShieldAlert}
        eyebrow="الإجراءات الجنائية"
        title="البلاغات الجنائية"
        description="متابعة البلاغات من التسجيل حتى الإحالة أو الحفظ، مع بيانات النيابة والمركز الأمني والخصوم."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {total.toLocaleString("ar-EG")} بلاغ {status ? "مطابق" : "مسجّل"}
            </span>
          ) : null
        }
        actions={
          <Button
            asChild
            className="border-white/25 bg-[var(--color-gold-400)] text-[var(--color-navy-950)] hover:bg-[var(--color-gold-300)]"
          >
            <Link href="/criminal-reports/new">
              <Plus className="size-4" />
              بلاغ جديد
            </Link>
          </Button>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <OperationalKpiCard
          label="إجمالي البلاغات"
          value={statusCounts.all}
          hint="كل الملفات المسجّلة"
          icon={<ShieldAlert aria-hidden />}
          accent="navy"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="قيد التحقيق"
          value={statusCounts.UNDER_INVESTIGATION}
          hint="لا تزال قيد المتابعة"
          icon={<ShieldAlert aria-hidden />}
          accent="gold"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="إحالة"
          value={statusCounts.REFERRAL}
          hint="محالة للمحكمة"
          icon={<ShieldAlert aria-hidden />}
          accent="info"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="محفوظة"
          value={statusCounts.ARCHIVED + statusCounts.FINAL_ARCHIVED}
          hint={`حفظ: ${statusCounts.ARCHIVED} · نهائي: ${statusCounts.FINAL_ARCHIVED}`}
          icon={<ShieldAlert aria-hidden />}
          accent="success"
          valueLoading={statsLoading}
        />
      </div>

      <SectionCard title="البحث والتصفية" contentClassName="space-y-4">
        <SearchInput
          value={search}
          onChange={(value) => {
            setSearch(value);
            setPage(0);
          }}
          placeholder="بحث برقم البلاغ أو التهمة أو الموكل أو الخصم..."
        />
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--color-border)] pt-4">
          <CriminalReportStatusFilter
            value={status}
            onChange={(value) => {
              setStatus(value);
              setPage(0);
            }}
            counts={statusCounts}
          />
          {hasActiveFilters ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="h-8 gap-1.5 text-[var(--color-muted-foreground)]"
              onClick={clearFilters}
            >
              <RotateCcw className="size-3.5" />
              مسح التصفية
            </Button>
          ) : null}
        </div>
      </SectionCard>

      <SectionCard flush title={filteredLabel}>
        {error ? (
          <div className="p-5">
            <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
          </div>
        ) : loading ? (
          <PageLoading label="جاري تحميل البلاغات..." />
        ) : rows.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={ShieldAlert}
              title={hasActiveFilters ? "لا توجد نتائج" : "لا توجد بلاغات"}
              description={
                hasActiveFilters
                  ? "جرّب تعديل البحث أو إزالة التصفية."
                  : "ابدأ بتسجيل بلاغ جنائي جديد لمتابعة مساره."
              }
              action={
                hasActiveFilters ? (
                  <Button variant="outline" onClick={clearFilters}>
                    مسح التصفية
                  </Button>
                ) : (
                  <Button asChild>
                    <Link href="/criminal-reports/new">بلاغ جديد</Link>
                  </Button>
                )
              }
            />
          </div>
        ) : (
          <>
            <div className="hidden overflow-x-auto md:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-right">رقم البلاغ</TableHead>
                    <TableHead className="text-right">التهمة</TableHead>
                    <TableHead className="text-right">الموكل</TableHead>
                    <TableHead className="text-right">الخصم</TableHead>
                    <TableHead className="text-right">النيابة</TableHead>
                    <TableHead className="text-right">المحامي</TableHead>
                    <TableHead className="text-right">الحالة</TableHead>
                    <TableHead className="text-right">التسجيل</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow
                      key={row.id}
                      className="transition-colors hover:bg-[var(--color-navy-50)]/40"
                    >
                      <TableCell className="font-medium">
                        <Link
                          href={`/criminal-reports/${row.id}`}
                          className="text-[var(--color-foreground)] hover:text-[var(--color-gold-600)] hover:underline"
                        >
                          <span className="block font-semibold ltr-nums">{row.reportNumber}</span>
                          {row.securityCenter ? (
                            <span className="text-xs text-[var(--color-muted-foreground)]">
                              {row.securityCenter}
                            </span>
                          ) : null}
                        </Link>
                      </TableCell>
                      <TableCell className="max-w-[200px] truncate">{row.charge ?? "—"}</TableCell>
                      <TableCell>{row.clientNames ?? row.clientName ?? "—"}</TableCell>
                      <TableCell>{row.opponentNames ?? row.opponentName ?? "—"}</TableCell>
                      <TableCell className="max-w-[160px] truncate text-sm">
                        {row.prosecutionOffice ?? "—"}
                      </TableCell>
                      <TableCell className="text-sm">{row.assignedLawyerName ?? "—"}</TableCell>
                      <TableCell>
                        <Badge tone={CRIMINAL_REPORT_STATUS_TONE[row.status]}>
                          {CRIMINAL_REPORT_STATUS_AR[row.status]}
                        </Badge>
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-[var(--color-muted-foreground)]">
                        {formatDate(row.registrationDate)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="space-y-3 p-4 md:hidden">
              {rows.map((row) => (
                <Link
                  key={row.id}
                  href={`/criminal-reports/${row.id}`}
                  className="block rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm transition hover:border-[var(--color-gold-400)]/50"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="font-semibold ltr-nums text-[var(--color-gold-600)]">
                        {row.reportNumber}
                      </p>
                      <p className="mt-1 line-clamp-2 font-medium leading-snug">
                        {row.charge ?? "بدون تهمة"}
                      </p>
                      <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">
                        الموكل: {row.clientNames ?? row.clientName ?? "—"}
                      </p>
                      <p className="text-sm text-[var(--color-muted-foreground)]">
                        النيابة: {row.prosecutionOffice ?? "—"}
                      </p>
                    </div>
                    <Badge tone={CRIMINAL_REPORT_STATUS_TONE[row.status]}>
                      {CRIMINAL_REPORT_STATUS_AR[row.status]}
                    </Badge>
                  </div>
                  <p className="mt-3 text-xs text-[var(--color-muted-foreground)]">
                    التسجيل: {formatDate(row.registrationDate)}
                    {row.assignedLawyerName ? ` · ${row.assignedLawyerName}` : ""}
                  </p>
                </Link>
              ))}
            </div>

            <div className="border-t border-[var(--color-border)] px-4 py-3">
              <Pagination page={page} total={total} pageSize={PAGE_SIZE} onPageChange={setPage} />
            </div>
          </>
        )}
      </SectionCard>
    </div>
  );
}
