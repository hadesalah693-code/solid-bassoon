"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  Banknote,
  CheckCircle2,
  Plus,
  Receipt,
  RotateCcw,
  TrendingUp,
  Wallet,
} from "lucide-react";

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

import { invoicesApi } from "@/lib/api/invoices";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import {
  INVOICE_STATUS_AR,
  INVOICE_STATUS_OPTIONS,
  INVOICE_STATUS_TONE,
} from "@/lib/invoices/labels";
import { useDebounce } from "@/hooks/use-debounce";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney, paidPercent } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils/cn";
import type { ClientPerson } from "@/types/person";
import type { InvoiceListItemDto, InvoiceStatusDto } from "@/types/invoice";

const PAGE_SIZE = 20;

const selectFilterClassName = cn(
  "h-10 w-full min-w-0 max-w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-foreground)] shadow-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function personName(p: ClientPerson) {
  if (p.personType === "COMPANY") return p.companyName?.trim() || p.id;
  return p.fullName?.trim() || p.id;
}

const EMPTY_STATS = {
  total: 0,
  paid: 0,
  totalBilled: 0,
  totalCollected: 0,
  totalRemaining: 0,
};

export default function InvoicesPage() {
  return (
    <Suspense fallback={<PageLoading label="جاري تحميل الفواتير..." />}>
      <InvoicesPageInner />
    </Suspense>
  );
}

function InvoicesPageInner() {
  const searchParams = useSearchParams();
  const initialSearch = searchParams.get("search") ?? "";

  const [rows, setRows] = useState<InvoiceListItemDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [statsLoading, setStatsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState(initialSearch);
  const debouncedSearch = useDebounce(search, 350);
  const [status, setStatus] = useState<InvoiceStatusDto | "">("");
  const [clientId, setClientId] = useState("");
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [stats, setStats] = useState(EMPTY_STATS);
  const [fetchId, setFetchId] = useState(0);

  const hasActiveFilters = !!status || !!clientId || !!search.trim();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await personsApi.list({ role: "CLIENT", size: 200 });
        if (!cancelled) setClients(res.content);
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
        const res = await invoicesApi.statistics();
        if (!cancelled) setStats(res);
      } catch {
        if (!cancelled) setStats(EMPTY_STATS);
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
        const res = await invoicesApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          ...(status ? { status } : {}),
          ...(clientId ? { clientId } : {}),
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(
            e instanceof ApiRequestError ? e.message : "تعذّر تحميل الفواتير",
          );
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
  }, [page, debouncedSearch, status, clientId, fetchId]);

  function clearFilters() {
    setStatus("");
    setClientId("");
    setSearch("");
    setPage(0);
  }

  const filteredLabel = useMemo(
    () => (status ? INVOICE_STATUS_AR[status] : "جميع الفواتير"),
    [status],
  );

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={Receipt}
        eyebrow="المالية والمحاسبة"
        title="الفواتير"
        description="إصدار فواتير الأتعاب ومتابعة التحصيل — حالة الفاتورة، المبالغ المحصّلة، والمتأخرات."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {total.toLocaleString("ar-EG")} فاتورة
            </span>
          ) : null
        }
        actions={
          <Button
            asChild
            className="border-white/25 bg-[var(--color-gold-400)] text-[var(--color-navy-950)] hover:bg-[var(--color-gold-300)]"
          >
            <Link href="/invoices/new">
              <Plus className="size-4" />
              فاتورة جديدة
            </Link>
          </Button>
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <OperationalKpiCard
          label="إجمالي الفواتير"
          value={stats.total}
          hint="كل السجلات"
          icon={<Receipt aria-hidden />}
          accent="navy"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="فواتير مدفوعة"
          value={stats.paid}
          hint="مكتملة السداد"
          icon={<CheckCircle2 aria-hidden />}
          accent="success"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="إجمالي المُستحق"
          value={statsLoading ? "" : formatMoney(stats.totalBilled)}
          hint="قيمة كل الفواتير"
          icon={<TrendingUp aria-hidden />}
          accent="gold"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="المحصّل"
          value={statsLoading ? "" : formatMoney(stats.totalCollected)}
          hint="الدفعات المسجّلة"
          icon={<Banknote aria-hidden />}
          accent="info"
          valueLoading={statsLoading}
        />
        <OperationalKpiCard
          label="المتبقي"
          value={statsLoading ? "" : formatMoney(stats.totalRemaining)}
          hint="يحتاج تحصيل"
          icon={<Wallet aria-hidden />}
          accent="danger"
          valueLoading={statsLoading}
        />
      </div>

      <SectionCard title="البحث والتصفية" contentClassName="space-y-4">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="xl:col-span-2">
            <SearchInput
              value={search}
              onChange={(value) => {
                setSearch(value);
                setPage(0);
              }}
              placeholder="بحث برقم الفاتورة أو رقم القضية أو العميل..."
            />
          </div>
          <select
            className={selectFilterClassName}
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as InvoiceStatusDto | "");
              setPage(0);
            }}
            aria-label="حالة الفاتورة"
          >
            <option value="">كل الحالات</option>
            {INVOICE_STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <select
            className={selectFilterClassName}
            value={clientId}
            onChange={(e) => {
              setClientId(e.target.value);
              setPage(0);
            }}
            aria-label="العميل"
          >
            <option value="">كل العملاء</option>
            {clients.map((c) => (
              <option key={c.id} value={c.id}>
                {personName(c)}
              </option>
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
            <ApiErrorAlert
              message={error}
              onRetry={() => setFetchId((n) => n + 1)}
            />
          </div>
        ) : loading ? (
          <PageLoading label="جاري تحميل الفواتير..." />
        ) : rows.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={Receipt}
              title={hasActiveFilters ? "لا توجد نتائج" : "لا توجد فواتير"}
              description={
                hasActiveFilters
                  ? "جرّب تعديل البحث أو إزالة التصفية."
                  : "ابدأ بإصدار أول فاتورة أتعاب."
              }
              action={
                hasActiveFilters ? (
                  <Button variant="outline" onClick={clearFilters}>
                    مسح التصفية
                  </Button>
                ) : (
                  <Button asChild>
                    <Link href="/invoices/new">فاتورة جديدة</Link>
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
                    <TableHead className="text-right">رقم الفاتورة</TableHead>
                    <TableHead className="text-right">العميل</TableHead>
                    <TableHead className="text-right">القضية</TableHead>
                    <TableHead className="text-right">الإصدار</TableHead>
                    <TableHead className="text-right">الاستحقاق</TableHead>
                    <TableHead className="text-right">الإجمالي</TableHead>
                    <TableHead className="text-right">المدفوع</TableHead>
                    <TableHead className="text-right">المتبقي</TableHead>
                    <TableHead className="text-right">الحالة</TableHead>
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
                          href={`/invoices/${row.id}`}
                          className="text-[var(--color-foreground)] hover:text-[var(--color-gold-600)] hover:underline"
                        >
                          <span className="block font-semibold ltr-nums">
                            {row.invoiceNumber}
                          </span>
                        </Link>
                      </TableCell>
                      <TableCell>{row.clientName ?? "—"}</TableCell>
                      <TableCell className="ltr-nums">
                        {row.caseNumber ?? "—"}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-[var(--color-muted-foreground)]">
                        {row.issueDate ? formatDate(row.issueDate) : "—"}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-[var(--color-muted-foreground)]">
                        {row.dueDate ? formatDate(row.dueDate) : "—"}
                      </TableCell>
                      <TableCell className="whitespace-nowrap font-medium ltr-nums">
                        {formatMoney(row.total)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-[var(--color-success)] ltr-nums">
                        {formatMoney(row.amountPaid)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap font-semibold text-[var(--color-danger)] ltr-nums">
                        {formatMoney(row.remaining)}
                      </TableCell>
                      <TableCell>
                        <Badge tone={INVOICE_STATUS_TONE[row.status]}>
                          {INVOICE_STATUS_AR[row.status]}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="space-y-3 p-4 md:hidden">
              {rows.map((row) => {
                const pct = paidPercent(row.amountPaid, row.total);
                return (
                  <Link
                    key={row.id}
                    href={`/invoices/${row.id}`}
                    className="block rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm transition hover:border-[var(--color-gold-400)]/50"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="font-semibold ltr-nums text-[var(--color-gold-600)]">
                          {row.invoiceNumber}
                        </p>
                        <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
                          العميل: {row.clientName ?? "—"}
                        </p>
                        <p className="text-sm text-[var(--color-muted-foreground)] ltr-nums">
                          القضية: {row.caseNumber ?? "—"}
                        </p>
                      </div>
                      <Badge tone={INVOICE_STATUS_TONE[row.status]}>
                        {INVOICE_STATUS_AR[row.status]}
                      </Badge>
                    </div>
                    <div className="mt-3 space-y-1.5">
                      <div className="h-1.5 w-full overflow-hidden rounded-full bg-[var(--color-surface-muted)]">
                        <div
                          className="h-full rounded-full bg-[var(--color-success)]"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                      <p className="flex items-center justify-between text-xs text-[var(--color-muted-foreground)]">
                        <span className="ltr-nums">
                          {formatMoney(row.amountPaid)} / {formatMoney(row.total)}
                        </span>
                        <span>{pct.toLocaleString("ar-EG")}٪</span>
                      </p>
                    </div>
                  </Link>
                );
              })}
            </div>

            <div className="border-t border-[var(--color-border)] px-4 py-3">
              <Pagination
                page={page}
                total={total}
                pageSize={PAGE_SIZE}
                onPageChange={setPage}
              />
            </div>
          </>
        )}
      </SectionCard>
    </div>
  );
}
