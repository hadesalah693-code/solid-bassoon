"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Archive, LayoutGrid, Plus, Table as TableIcon, Briefcase } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SearchInput } from "@/components/shared/SearchInput";
import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";
import { ApiRequestError } from "@/lib/api/client";
import { casesApi } from "@/lib/api/cases";
import { caseFileFromDto } from "@/types/api";
import { useDebounce } from "@/hooks/use-debounce";

import { CaseCard } from "@/components/cases/CaseCard";
import {
  CasePriorityBadge,
  CaseStatusBadge,
} from "@/components/shared/StatusBadge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate } from "@/lib/utils/format-date";
import type { CaseFile, CasePriority } from "@/types/case";

type ViewMode = "grid" | "table";

type StatusFilterKey =
  | "all"
  | "active"
  | "on_hold"
  | "closed"
  | "archived";

const STATUS_FILTERS: { key: StatusFilterKey; label: string }[] = [
  { key: "all", label: "الكل" },
  { key: "active", label: "نشطة" },
  { key: "on_hold", label: "معلّقة" },
  { key: "closed", label: "مغلقة" },
  { key: "archived", label: "مؤرشفة" },
];

function itemMatchesStatusFilter(
  c: CaseFile,
  filter: StatusFilterKey,
): boolean {
  if (filter === "all") return true;
  if (filter === "active") {
    return c.status === "NEW" || c.status === "IN_PROGRESS";
  }
  if (filter === "on_hold") return c.status === "ON_HOLD";
  if (filter === "closed") return c.status === "CLOSED";
  if (filter === "archived") return c.status === "ARCHIVED";
  return true;
}

function CasesPageInner() {
  const searchParams = useSearchParams();
  const qParam = searchParams.get("q") ?? "";
  const [search, setSearch] = useState(qParam);
  const [view, setView] = useState<ViewMode>("grid");
  const [showArchived, setShowArchived] = useState(false);
  const [statusFilter, setStatusFilter] = useState<StatusFilterKey>("all");
  const [priority, setPriority] = useState<CasePriority | "">("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const debounced = useDebounce(search, 300);

  useEffect(() => {
    setSearch(qParam);
  }, [qParam]);
  const [items, setItems] = useState<CaseFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await casesApi.list({
          page,
          size: 20,
          search: debounced.trim() || undefined,
          includeArchived: showArchived,
          priority: priority || undefined,
          fromDate: fromDate || undefined,
          toDate: toDate || undefined,
        });
        if (cancelled) return;
        setItems(res.content.map(caseFileFromDto));
        setTotalPages(res.totalPages);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل القضايا");
        }
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [debounced, fetchId, showArchived, priority, fromDate, toDate, page]);

  const statusCounts = useMemo(() => {
    const base = {
      all: items.length,
      active: 0,
      on_hold: 0,
      closed: 0,
      archived: 0,
    };
    for (const c of items) {
      if (c.status === "NEW" || c.status === "IN_PROGRESS") base.active += 1;
      else if (c.status === "ON_HOLD") base.on_hold += 1;
      else if (c.status === "CLOSED") base.closed += 1;
      else if (c.status === "ARCHIVED") base.archived += 1;
    }
    return base;
  }, [items]);

  const filteredItems = useMemo(
    () => items.filter((c) => itemMatchesStatusFilter(c, statusFilter)),
    [items, statusFilter],
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title="القضايا"
        description="جميع قضايا المكتب، نشطة كانت أم مؤرشفة."
        actions={
          <Button variant="gold" asChild>
            <Link
              href="/cases/new"
              className="inline-flex items-center gap-2"
            >
              <Plus className="size-4" />
              قضية جديدة
            </Link>
          </Button>
        }
      />

      {error ? (
        <ApiErrorAlert
          message={error}
          onRetry={() => setFetchId((n) => n + 1)}
        />
      ) : null}

      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="ابحث برقم القضية، العنوان، أو اسم الموكّل..."
          className="md:max-w-md"
        />
        <div className="flex flex-wrap items-center gap-2 self-end">
          <button
            type="button"
            onClick={() => setShowArchived((v) => !v)}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-xl border px-3 py-1.5 text-xs font-medium transition-colors",
              showArchived
                ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)] text-[var(--color-gold-700)]"
                : "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
            )}
            aria-pressed={showArchived}
          >
            <Archive className="size-3.5" />
            {showArchived ? "يشمل المؤرشف" : "إظهار المؤرشف"}
          </button>
          <div className="flex items-center gap-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-1">
            <ViewToggle
              current={view}
              value="grid"
              onClick={() => setView("grid")}
              icon={LayoutGrid}
              label="بطاقات"
            />
            <ViewToggle
              current={view}
              value="table"
              onClick={() => setView("table")}
              icon={TableIcon}
              label="جدول"
            />
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <label className="space-y-1 text-xs text-[var(--color-muted-foreground)]">الأولوية
          <select value={priority} onChange={(e) => { setPriority(e.target.value as CasePriority | ""); setPage(0); }} className="block h-10 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm text-[var(--color-foreground)]">
            <option value="">كل الأولويات</option><option value="LOW">منخفضة</option><option value="MEDIUM">متوسطة</option><option value="HIGH">عالية</option><option value="URGENT">عاجلة</option>
          </select>
        </label>
        <label className="space-y-1 text-xs text-[var(--color-muted-foreground)]">من تاريخ
          <input type="date" value={fromDate} onChange={(e) => { setFromDate(e.target.value); setPage(0); }} className="block h-10 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm text-[var(--color-foreground)]" />
        </label>
        <label className="space-y-1 text-xs text-[var(--color-muted-foreground)]">إلى تاريخ
          <input type="date" value={toDate} onChange={(e) => { setToDate(e.target.value); setPage(0); }} className="block h-10 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm text-[var(--color-foreground)]" />
        </label>
      </div>

      {!loading && !error && items.length > 0 ? (
        <div
          className="flex flex-wrap gap-2"
          role="tablist"
          aria-label="تصفية حسب الحالة"
        >
          {STATUS_FILTERS.map(({ key, label }) => {
            const count = statusCounts[key];
            const active = statusFilter === key;
            return (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => setStatusFilter(key)}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors",
                  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-background)]",
                  active
                    ? "border-[var(--color-gold-400)] bg-[var(--color-navy-800)] text-white shadow-[var(--shadow-soft)]"
                    : "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-navy-800)] hover:border-[var(--color-gold-300)]",
                )}
              >
                {label}
                <span
                  className={cn(
                    "ltr-nums rounded-md px-1 py-0 text-[10px] font-bold tabular-nums",
                    active
                      ? "bg-white/15 text-white"
                      : "bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
                  )}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>
      ) : null}

      {loading ? (
        <PageLoading
          label="جارٍ تحميل القضايا…"
          minHeightClassName="min-h-[18rem]"
        />
      ) : error ? null : items.length === 0 ? (
        <EmptyState
          title={search.trim() ? "لا نتائج للبحث" : "لا توجد قضايا"}
          description={
            search.trim()
              ? "لا توجد قضايا تطابق نص البحث الحالي. أفرغ الحقل لعرض الكل (ضمن حد الاستعلام) أو جرّب كلمات أخرى."
              : "لا توجد قضايا مسجّلة بعد. اضغط «قضية جديدة» لإضافة أول قضية."
          }
          icon={Briefcase}
        />
      ) : filteredItems.length === 0 ? (
        <EmptyState
          title="لا قضايا بهذه الحالة"
          description="جرّب فلترًا آخر أو أعد إظهار المؤرشف إن كنت تبحث عن قضايا مؤرشفة."
          icon={Briefcase}
        />
      ) : view === "grid" ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {filteredItems.map((c) => (
            <CaseCard key={c.id} data={c} />
          ))}
        </div>
      ) : (
        <SectionCard flush>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>رقم القضية</TableHead>
                <TableHead>العنوان</TableHead>
                <TableHead>الموكّل</TableHead>
                <TableHead>المحامي</TableHead>
                <TableHead>الحالة</TableHead>
                <TableHead>الأولوية</TableHead>
                <TableHead className="text-end">البداية</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredItems.map((c) => (
                <TableRow key={c.id}>
                  <TableCell className="font-medium ltr-nums">
                    <Link
                      href={`/cases/${c.id}`}
                      className="text-[var(--color-navy-700)] hover:underline"
                    >
                      #{c.caseNumber}
                    </Link>
                  </TableCell>
                  <TableCell className="max-w-xs truncate">
                    {c.title}
                  </TableCell>
                  <TableCell>{c.client.fullName}</TableCell>
                  <TableCell>
                    {c.assignedLawyer?.fullName ?? "—"}
                  </TableCell>
                  <TableCell>
                    <CaseStatusBadge value={c.status} />
                  </TableCell>
                  <TableCell>
                    <CasePriorityBadge value={c.priority} />
                  </TableCell>
                  <TableCell className="text-end text-xs text-[var(--color-muted-foreground)]">
                    {c.startDate ? formatDate(c.startDate) : "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </SectionCard>
      )}
      {!loading && !error && totalPages > 1 ? <div className="flex items-center justify-between"><Button variant="outline" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>السابق</Button><span className="text-sm">{page + 1} / {totalPages}</span><Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => setPage((current) => current + 1)}>التالي</Button></div> : null}
    </div>
  );
}

export default function CasesPage() {
  return (
    <Suspense
      fallback={
        <div className="space-y-6">
          <PageLoading
            label="جارٍ تحميل الصفحة…"
            minHeightClassName="min-h-[18rem]"
            className="w-full"
          />
        </div>
      }
    >
      <CasesPageInner />
    </Suspense>
  );
}

function ViewToggle({
  current,
  value,
  onClick,
  icon: Icon,
  label,
}: {
  current: ViewMode;
  value: ViewMode;
  onClick: () => void;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}) {
  const active = current === value;
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors",
        active
          ? "bg-[var(--color-navy-800)] text-white"
          : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
      )}
    >
      <Icon className="size-3.5" />
      {label}
    </button>
  );
}
