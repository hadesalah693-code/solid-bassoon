"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CalendarClock, CheckCircle2, Download, Handshake, Plus, RotateCcw, XCircle } from "lucide-react";

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

import { mandatesApi } from "@/lib/api/mandates";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  MANDATE_PAYMENT_STATUS_AR,
  MANDATE_PAYMENT_STATUS_TONE,
  MANDATE_PRIORITY_AR,
  MANDATE_PRIORITY_OPTIONS,
  MANDATE_PRIORITY_TONE,
  MANDATE_STATUS_AR,
  MANDATE_STATUS_TONE,
} from "@/lib/mandates/labels";
import { useDebounce } from "@/hooks/use-debounce";
import { formatDate } from "@/lib/utils/format-date";
import { cn } from "@/lib/utils/cn";
import type { ClientPerson } from "@/types/person";
import type {
  MandateListItemDto,
  MandatePaymentStatusDto,
  MandatePriorityDto,
  MandateStatusDto,
} from "@/types/mandate";

const PAGE_SIZE = 20;

const selectFilterClassName = cn(
  "h-10 w-full min-w-0 max-w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-foreground)] shadow-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function personName(p: ClientPerson) {
  if (p.personType === "COMPANY") return p.companyName?.trim() || p.id;
  return p.fullName?.trim() || p.id;
}

export default function MandatesPage() {
  return (
    <Suspense fallback={<PageLoading label="جاري تحميل الإنابات..." />}>
      <MandatesPageInner />
    </Suspense>
  );
}

function MandatesPageInner() {
  const searchParams = useSearchParams();
  const initialSearch = searchParams.get("search") ?? "";

  const [rows, setRows] = useState<MandateListItemDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [statsLoading, setStatsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState(initialSearch);
  const debouncedSearch = useDebounce(search, 350);
  const [status, setStatus] = useState<MandateStatusDto | "">("");
  const [priority, setPriority] = useState<MandatePriorityDto | "">("");
  const [paymentStatus, setPaymentStatus] = useState<MandatePaymentStatusDto | "">("");
  const [lawyerId, setLawyerId] = useState("");
  const [lawyers, setLawyers] = useState<ClientPerson[]>([]);
  const [stats, setStats] = useState({
    total: 0,
    today: 0,
    upcoming: 0,
    completed: 0,
    cancelled: 0,
    totalFees: "0",
    totalPaid: "0",
    totalRemaining: "0",
  });
  const [fetchId, setFetchId] = useState(0);
  const [exporting, setExporting] = useState(false);

  const hasActiveFilters = !!status || !!priority || !!paymentStatus || !!search.trim() || !!lawyerId;

  async function handleExport() {
    setExporting(true);
    try {
      const blob = await mandatesApi.exportCsv({
        search: debouncedSearch,
        ...(status ? { status } : {}),
        ...(priority ? { priority } : {}),
        ...(paymentStatus ? { paymentStatus } : {}),
        ...(lawyerId ? { originalLawyerId: lawyerId } : {}),
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `mandates-${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      notifySuccess("تم تنزيل الملف");
    } catch (e) {
      notifyApiError(e, "تعذّر تصدير الإنابات");
    } finally {
      setExporting(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [lRes, sRes] = await Promise.all([
          personsApi.list({ role: "LAWYER", size: 200 }),
          personsApi.list({ role: "SENIOR_LAWYER", size: 200 }),
        ]);
        if (!cancelled) {
          const merged = [...lRes.content, ...sRes.content];
          const seen = new Set<string>();
          setLawyers(merged.filter((p) => !seen.has(p.id) && seen.add(p.id)));
        }
      } catch {
        /* optional filter */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setStatsLoading(true);
      try {
        const res = await mandatesApi.statistics();
        if (!cancelled) setStats(res);
      } catch {
        if (!cancelled) {
          setStats({ total: 0, today: 0, upcoming: 0, completed: 0, cancelled: 0, totalFees: "0", totalPaid: "0", totalRemaining: "0" });
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
        const res = await mandatesApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          ...(status ? { status } : {}),
          ...(priority ? { priority } : {}),
          ...(paymentStatus ? { paymentStatus } : {}),
          ...(lawyerId ? { originalLawyerId: lawyerId } : {}),
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل الإنابات");
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
  }, [page, debouncedSearch, status, priority, paymentStatus, lawyerId, fetchId]);

  function clearFilters() {
    setStatus("");
    setPriority("");
    setPaymentStatus("");
    setLawyerId("");
    setSearch("");
    setPage(0);
  }

  const filteredLabel = useMemo(() => {
    if (!status) return "جميع الإنابات";
    return MANDATE_STATUS_AR[status];
  }, [status]);

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={Handshake}
        eyebrow="الإدارة القانونية"
        title="الإنابات"
        description="متابعة إنابات المحامين في الجلسات والقضايا — المحامي الأصلي والمُنتدب، الجلسات، النتائج، والدفعات."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {total.toLocaleString("ar-EG")} إنابة
            </span>
          ) : null
        }
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="ghost"
              onClick={() => void handleExport()}
              disabled={exporting || loading}
              className="border-white/25 bg-white/5 text-white hover:bg-white/15 hover:text-white"
            >
              <Download className="size-4" />
              {exporting ? "جارٍ التصدير..." : "تصدير"}
            </Button>
            <Button
              asChild
              className="border-white/25 bg-[var(--color-gold-400)] text-[var(--color-navy-950)] hover:bg-[var(--color-gold-300)]"
            >
              <Link href="/mandates/new">
                <Plus className="size-4" />
                إنابة جديدة
              </Link>
            </Button>
          </div>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <OperationalKpiCard
          label="إجمالي الإنابات"
          value={stats.total}
          hint="كل السجلات"
          icon={<Handshake aria-hidden />}
          accent="navy"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="جلسات اليوم"
          value={stats.today}
          hint="إنابات بجلسة اليوم"
          icon={<CalendarClock aria-hidden />}
          accent="gold"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="قادمة"
          value={stats.upcoming}
          hint="بجلسات مستقبلية"
          icon={<CalendarClock aria-hidden />}
          accent="info"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="مكتملة"
          value={stats.completed}
          hint="تم إنجازها"
          icon={<CheckCircle2 aria-hidden />}
          accent="success"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="ملغاة"
          value={stats.cancelled}
          hint="ألغيت"
          icon={<XCircle aria-hidden />}
          accent="danger"
          valueLoading={statsLoading}
        />
      </div>

      <SectionCard title="البحث والتصفية" contentClassName="space-y-4">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
          <div className="xl:col-span-2">
            <SearchInput
              value={search}
              onChange={(value) => {
                setSearch(value);
                setPage(0);
              }}
              placeholder="بحث برقم الإنابة أو رقم القضية أو العميل..."
            />
          </div>
          <select
            className={selectFilterClassName}
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as MandateStatusDto | "");
              setPage(0);
            }}
            aria-label="حالة الإنابة"
          >
            <option value="">كل الحالات</option>
            {(Object.entries(MANDATE_STATUS_AR) as [MandateStatusDto, string][]).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
          <select
            className={selectFilterClassName}
            value={priority}
            onChange={(e) => {
              setPriority(e.target.value as MandatePriorityDto | "");
              setPage(0);
            }}
            aria-label="الأولوية"
          >
            <option value="">كل الأولويات</option>
            {MANDATE_PRIORITY_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          <select
            className={selectFilterClassName}
            value={paymentStatus}
            onChange={(e) => {
              setPaymentStatus(e.target.value as MandatePaymentStatusDto | "");
              setPage(0);
            }}
            aria-label="حالة الدفع"
          >
            <option value="">كل حالات الدفع</option>
            {(Object.entries(MANDATE_PAYMENT_STATUS_AR) as [MandatePaymentStatusDto, string][]).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
          <select
            className={selectFilterClassName}
            value={lawyerId}
            onChange={(e) => {
              setLawyerId(e.target.value);
              setPage(0);
            }}
            aria-label="المحامي المُنتدب"
          >
            <option value="">كل المحامين</option>
            {lawyers.map((l) => (
              <option key={l.id} value={l.id}>{personName(l)}</option>
            ))}
          </select>
        </div>
        {hasActiveFilters ? (
          <div className="flex items-center justify-end border-t border-[var(--color-border)] pt-3">
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
          </div>
        ) : null}
      </SectionCard>

      <SectionCard flush title={filteredLabel}>
        {error ? (
          <div className="p-5">
            <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
          </div>
        ) : loading ? (
          <PageLoading label="جاري تحميل الإنابات..." />
        ) : rows.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={Handshake}
              title={hasActiveFilters ? "لا توجد نتائج" : "لا توجد إنابات"}
              description={
                hasActiveFilters
                  ? "جرّب تعديل البحث أو إزالة التصفية."
                  : "ابدأ بتسجيل إنابة محامٍ جديدة."
              }
              action={
                hasActiveFilters ? (
                  <Button variant="outline" onClick={clearFilters}>
                    مسح التصفية
                  </Button>
                ) : (
                  <Button asChild>
                    <Link href="/mandates/new">إنابة جديدة</Link>
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
                    <TableHead className="text-right">رقم الإنابة</TableHead>
                    <TableHead className="text-right">القضية</TableHead>
                    <TableHead className="text-right">العميل</TableHead>
                    <TableHead className="text-right">المحامي المُنتدب</TableHead>
                    <TableHead className="text-right">الجلسة</TableHead>
                    <TableHead className="text-right">الحالة</TableHead>
                    <TableHead className="text-right">الأولوية</TableHead>
                    <TableHead className="text-right">الدفع</TableHead>
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
                          href={`/mandates/${row.id}`}
                          className="text-[var(--color-foreground)] hover:text-[var(--color-gold-600)] hover:underline"
                        >
                          <span className="block font-semibold ltr-nums">
                            {row.mandateNumber}
                          </span>
                        </Link>
                      </TableCell>
                      <TableCell className="max-w-[220px]">
                        <p className="truncate font-medium ltr-nums">{row.caseNumber}</p>
                        {row.caseTitle ? (
                          <p className="truncate text-xs text-[var(--color-muted-foreground)]">
                            {row.caseTitle}
                          </p>
                        ) : null}
                      </TableCell>
                      <TableCell>
                        {row.client?.fullName ?? "—"}
                      </TableCell>
                      <TableCell>{row.substitutedLawyer?.fullName ?? "—"}</TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-[var(--color-muted-foreground)]">
                        {row.sessionDate ? formatDate(row.sessionDate) : "—"}
                      </TableCell>
                      <TableCell>
                        <Badge tone={MANDATE_STATUS_TONE[row.status]}>
                          {MANDATE_STATUS_AR[row.status]}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge tone={MANDATE_PRIORITY_TONE[row.priority]}>
                          {MANDATE_PRIORITY_AR[row.priority]}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge tone={MANDATE_PAYMENT_STATUS_TONE[row.paymentStatus]}>
                          {MANDATE_PAYMENT_STATUS_AR[row.paymentStatus]}
                        </Badge>
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
                  href={`/mandates/${row.id}`}
                  className="block rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm transition hover:border-[var(--color-gold-400)]/50"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="font-semibold ltr-nums text-[var(--color-gold-600)]">
                        {row.mandateNumber}
                      </p>
                      <p className="mt-1 line-clamp-1 font-medium ltr-nums">{row.caseNumber}</p>
                      <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">
                        العميل: {row.client?.fullName ?? "—"}
                      </p>
                      <p className="text-sm text-[var(--color-muted-foreground)]">
                        المحامي المُنتدب: {row.substitutedLawyer?.fullName ?? "—"}
                      </p>
                    </div>
                    <Badge tone={MANDATE_STATUS_TONE[row.status]}>
                      {MANDATE_STATUS_AR[row.status]}
                    </Badge>
                  </div>
                  <p className="mt-3 text-xs text-[var(--color-muted-foreground)]">
                    {row.sessionDate ? `الجلسة: ${formatDate(row.sessionDate)}` : "بدون جلسة"}
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
