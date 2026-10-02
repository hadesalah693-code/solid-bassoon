"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowDownUp, FileSignature, LayoutGrid, List, Plus, RotateCcw, Download, Settings2 } from "lucide-react";
import { DelegationCard } from "@/components/delegations/DelegationCard";
import { DelegationListTable } from "@/components/delegations/DelegationListTable";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { Pagination } from "@/components/shared/Pagination";
import { SearchInput } from "@/components/shared/SearchInput";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { delegationsApi } from "@/lib/api/delegations";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import {
  DELEGATION_STATUS_AR,
  DELEGATION_TYPE_AR,
  ISSUING_COUNTRIES,
} from "@/lib/delegations/labels";
import { useDebounce } from "@/hooks/use-debounce";
import { cn } from "@/lib/utils/cn";
import { formatTotalCountAr } from "@/lib/delegations/format-duration";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import type { DelegationListItemDto, DelegationStatusDto, DelegationTypeDto } from "@/types/delegation";
import type { ClientPerson } from "@/types/person";

const DEFAULT_PAGE_SIZE = 12;
const PAGE_SIZE_OPTIONS = [12, 24, 48] as const;
const VIEW_STORAGE_KEY = "kalima-delegations-view";

type ViewMode = "cards" | "table";

const selectFilterClassName = cn(
  "h-10 w-full min-w-0 max-w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-foreground)] shadow-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function staffLabel(p: ClientPerson) {
  return p.fullName?.trim() || p.id;
}

function DelegationsPageInner() {
  const searchParams = useSearchParams();
  const initialSearch = searchParams.get("search") ?? "";
  const initialClientId = searchParams.get("clientId") ?? "";
  const [rows, setRows] = useState<DelegationListItemDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<number>(DEFAULT_PAGE_SIZE);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState(initialSearch);
  const debouncedSearch = useDebounce(search, 350);
  const [status, setStatus] = useState<DelegationStatusDto | "">("");
  const [type, setType] = useState<DelegationTypeDto | "">("");
  const [country, setCountry] = useState("");
  const [agentId, setAgentId] = useState("");
  const clientId = initialClientId;
  const [starredOnly, setStarredOnly] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>("cards");
  const [exporting, setExporting] = useState(false);
  const [lawyers, setLawyers] = useState<ClientPerson[]>([]);

  useEffect(() => {
    const stored = window.localStorage.getItem(VIEW_STORAGE_KEY);
    if (stored === "cards" || stored === "table") {
      setViewMode(stored);
    }
  }, []);

  function handleViewChange(mode: ViewMode) {
    setViewMode(mode);
    window.localStorage.setItem(VIEW_STORAGE_KEY, mode);
  }

  function handleStarChange(id: string, starred: boolean) {
    setRows((prev) => prev.map((r) => (r.id === id ? { ...r, starred } : r)));
  }

  async function handleExport() {
    setExporting(true);
    try {
      const blob = await delegationsApi.exportCsv({
        search: debouncedSearch,
        ...(status ? { status } : {}),
        ...(type ? { type } : {}),
        ...(country ? { issuingCountry: country } : {}),
        ...(clientId ? { clientId } : {}),
        ...(agentId ? { agentId } : {}),
        ...(starredOnly ? { starredOnly: true } : {}),
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `delegations-${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      notifySuccess("تم تنزيل الملف");
    } catch (e) {
      notifyApiError(e, "تعذّر تصدير التوكيلات");
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
      setLoading(true);
      setError(null);
      try {
        const res = await delegationsApi.list({
          page,
          size: pageSize,
          search: debouncedSearch,
          ...(status ? { status } : {}),
          ...(type ? { type } : {}),
          ...(country ? { issuingCountry: country } : {}),
          ...(clientId ? { clientId } : {}),
          ...(agentId ? { agentId } : {}),
          ...(starredOnly ? { starredOnly: true } : {}),
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل التوكيلات");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, pageSize, debouncedSearch, status, type, country, clientId, agentId, starredOnly]);

  function resetFilters() {
    setSearch("");
    setStatus("");
    setType("");
    setCountry("");
    setAgentId("");
    setStarredOnly(false);
    setPage(0);
  }

  return (
    <div className="space-y-6 pb-10">
      <DisputeModuleHero
        eyebrow="إدارة التوكيلات"
        title="التوكيلات"
        description="متابعة وكالات التوكيل للعملاء — الحالة، التواريخ، والتذكيرات."
        icon={FileSignature}
        actions={
          <>
            <Button
              type="button"
              variant="outline"
              className="gap-2 border-white/25 bg-white/5 text-white hover:bg-white/10"
              disabled={exporting}
              onClick={() => void handleExport()}
            >
              <Download className="size-4" aria-hidden />
              {exporting ? "جاري التصدير..." : "تصدير CSV"}
            </Button>
            <Button
              asChild
              variant="outline"
              className="gap-2 border-white/25 bg-white/5 text-white hover:bg-white/10"
            >
              <Link href="/delegations/settings">
                <Settings2 className="size-4" aria-hidden />
                الإعدادات
              </Link>
            </Button>
            <Button asChild className="gap-2 bg-[var(--color-gold-400)] text-[var(--color-navy-900)] hover:bg-[var(--color-gold-300)]">
              <Link href="/delegations/new">
                <Plus className="size-4" aria-hidden />
                توكيل جديد
              </Link>
            </Button>
          </>
        }
      />

      <SectionCard
        title="بحث وتصفية"
        contentClassName="space-y-4"
      >
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
          <div className="xl:col-span-2">
            <SearchInput
              value={search}
              onChange={(value) => {
                setSearch(value);
                setPage(0);
              }}
              placeholder="بحث برقم التوكيل أو اسم الموكل..."
            />
          </div>
          <select
            className={selectFilterClassName}
            value={type}
            onChange={(e) => {
              setType(e.target.value as DelegationTypeDto | "");
              setPage(0);
            }}
            aria-label="نوع التوكيل"
          >
            <option value="">كل الأنواع</option>
            {(Object.entries(DELEGATION_TYPE_AR) as [DelegationTypeDto, string][]).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
          <select
            className={selectFilterClassName}
            value={country}
            onChange={(e) => {
              setCountry(e.target.value);
              setPage(0);
            }}
            aria-label="بلد الإصدار"
          >
            <option value="">كل البلدان</option>
            {ISSUING_COUNTRIES.map((c) => (
              <option key={c.code} value={c.code}>{c.flag} {c.label}</option>
            ))}
          </select>
          <select
            className={selectFilterClassName}
            value={agentId}
            onChange={(e) => {
              setAgentId(e.target.value);
              setPage(0);
            }}
            aria-label="الوكيل"
          >
            <option value="">كل الوكلاء</option>
            {lawyers.map((l) => (
              <option key={l.id} value={l.id}>{staffLabel(l)}</option>
            ))}
          </select>
          <select
            className={selectFilterClassName}
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as DelegationStatusDto | "");
              setPage(0);
            }}
            aria-label="الحالة"
          >
            <option value="">كل الحالات</option>
            {(Object.entries(DELEGATION_STATUS_AR) as [DelegationStatusDto, string][]).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="accent-[var(--color-gold-500)]"
              checked={starredOnly}
              onChange={(e) => {
                setStarredOnly(e.target.checked);
                setPage(0);
              }}
            />
            المميزة فقط
          </label>
          <div className="inline-flex rounded-xl bg-[var(--color-surface-muted)] p-1 ring-1 ring-[var(--color-border)]/60">
            <button
              type="button"
              className={cn(
                "flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors",
                viewMode === "cards" ?
                  "bg-[var(--color-surface)] text-[var(--color-foreground)] shadow-[var(--shadow-soft)]"
                : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
              )}
              onClick={() => handleViewChange("cards")}
            >
              <LayoutGrid className="size-3.5" aria-hidden />
              بطاقات
            </button>
            <button
              type="button"
              className={cn(
                "flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors",
                viewMode === "table" ?
                  "bg-[var(--color-surface)] text-[var(--color-foreground)] shadow-[var(--shadow-soft)]"
                : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
              )}
              onClick={() => handleViewChange("table")}
            >
              <List className="size-3.5" aria-hidden />
              قائمة
            </button>
          </div>
          <Button type="button" variant="ghost" size="sm" className="gap-2" onClick={resetFilters}>
            <RotateCcw className="size-3.5" aria-hidden />
            إعادة ضبط
          </Button>
        </div>
      </SectionCard>

      {error ? <ApiErrorAlert message={error} /> : null}

      {loading ?
        <PageLoading label="جاري تحميل التوكيلات..." />
      : rows.length === 0 ?
        <EmptyState
          title="لا توجد توكيلات"
          description="أنشئ توكيلاً جديداً أو غيّر معايير البحث."
          action={
            <Button asChild>
              <Link href="/delegations/new">توكيل جديد</Link>
            </Button>
          }
        />
      : <>
          <div className="flex flex-wrap items-center justify-between gap-3 px-1">
            <p className="text-sm font-semibold text-[var(--color-foreground)]">
              {formatTotalCountAr(total, "توكيل", "توكيلات")}
            </p>
            <div className="flex items-center gap-2 text-xs text-[var(--color-muted-foreground)]">
              <ArrowDownUp className="size-3.5" aria-hidden />
              <span>الترتيب: الأحدث</span>
            </div>
          </div>
          {viewMode === "cards" ?
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {rows.map((d) => (
                <DelegationCard key={d.id} delegation={d} onStarChange={handleStarChange} />
              ))}
            </div>
          : <DelegationListTable rows={rows} onStarChange={handleStarChange} />}
          {total > 0 ?
            <div className="space-y-3 border-t border-[var(--color-border)] pt-3">
              <div className="flex flex-wrap items-center justify-between gap-3 px-2 text-sm text-[var(--color-muted-foreground)]">
                <label className="flex items-center gap-2">
                  <span>عرض</span>
                  <select
                    className={cn(selectFilterClassName, "h-8 w-auto min-w-[4.5rem] py-1 text-xs")}
                    value={pageSize}
                    onChange={(e) => {
                      setPageSize(Number(e.target.value));
                      setPage(0);
                    }}
                    aria-label="عدد العناصر في الصفحة"
                  >
                    {PAGE_SIZE_OPTIONS.map((n) => (
                      <option key={n} value={n}>{n}</option>
                    ))}
                  </select>
                  <span>في الصفحة</span>
                </label>
              </div>
              {total > pageSize ?
                <Pagination
                  page={page}
                  pageSize={pageSize}
                  total={total}
                  onPageChange={setPage}
                />
              : null}
            </div>
          : null}
        </>}
    </div>
  );
}

export default function DelegationsPage() {
  return (
    <Suspense fallback={<PageLoading label="جاري تحميل التوكيلات..." />}>
      <DelegationsPageInner />
    </Suspense>
  );
}
