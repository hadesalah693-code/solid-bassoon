"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Handshake, Plus, RotateCcw, SlidersHorizontal } from "lucide-react";

import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Pagination } from "@/components/shared/Pagination";
import { SearchInput } from "@/components/shared/SearchInput";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { disputesApi } from "@/lib/api/disputes";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { cn } from "@/lib/utils/cn";
import { formatDate } from "@/lib/utils/format-date";
import {
  DISPUTE_STATUS_AR,
  DISPUTE_TYPE_AR,
  DISPUTE_WORKFLOW_STEP_AR,
  LEGAL_PATH_AR,
  RISK_LEVEL_AR,
} from "@/lib/disputes/labels";
import { useDebounce } from "@/hooks/use-debounce";
import type {
  DisputeListItemDto,
  DisputeStatusDto,
  DisputeTypeDto,
  DisputeWorkflowStepDto,
} from "@/types/dispute";
import type { ClientPerson } from "@/types/person";

const PAGE_SIZE = 10;

const selectFilterClassName = cn(
  "h-10 w-full min-w-0 max-w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-foreground)] shadow-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function statusTone(s: DisputeListItemDto["status"]) {
  switch (s) {
    case "CONVERTED_TO_CASE":
    case "CLOSED":
      return "success" as const;
    case "REJECTED":
      return "danger" as const;
    case "READY_TO_CONVERT":
      return "gold" as const;
    case "NEW":
    case "UNDER_REVIEW":
      return "info" as const;
    default:
      return "neutral" as const;
  }
}

function riskTone(r: DisputeListItemDto["riskLevel"]) {
  switch (r) {
    case "LOW":
      return "neutral" as const;
    case "MEDIUM":
      return "info" as const;
    case "HIGH":
      return "warning" as const;
    case "CRITICAL":
      return "danger" as const;
  }
}

function partyLabel(p: DisputeListItemDto["client"]) {
  if (!p?.fullName) return "—";
  return p.fullName;
}

function lawyerLabel(p: DisputeListItemDto["assignedLawyer"]) {
  if (!p?.fullName) return "—";
  return p.fullName;
}

function shortDisputeNo(id: string) {
  return id.replace(/-/g, "").slice(0, 8).toUpperCase();
}

function lawyerPickerLabel(p: ClientPerson) {
  if (p.personType === "COMPANY") {
    return p.companyName?.trim() || p.fullName?.trim() || p.id;
  }
  return p.fullName?.trim() || p.id;
}

function disputeDisplayRef(row: { id: string; disputeNumber?: string | null }) {
  const n = row.disputeNumber?.trim();
  return n || shortDisputeNo(row.id);
}

function moneyAr(n: number | null) {
  if (n == null) return "—";
  try {
    return new Intl.NumberFormat("ar-EG", {
      maximumFractionDigits: 2,
    }).format(n);
  } catch {
    return String(n);
  }
}

function DisputesPageInner() {
  const searchParams = useSearchParams();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState(searchParams.get("q") ?? "");
  const [statusF, setStatusF] = useState<DisputeStatusDto | "">("");
  const [typeF, setTypeF] = useState<DisputeTypeDto | "">("");
  const [stepF, setStepF] = useState<DisputeWorkflowStepDto | "">("");
  const [lawyerF, setLawyerF] = useState("");
  const [lawyersPick, setLawyersPick] = useState<ClientPerson[]>([]);
  const debouncedSearch = useDebounce(search, 300);

  const [items, setItems] = useState<DisputeListItemDto[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [lRes, sRes] = await Promise.all([
          personsApi.list({ role: "LAWYER", size: 200 }),
          personsApi.list({ role: "SENIOR_LAWYER", size: 200 }),
        ]);
        if (cancelled) return;
        const lawMap = new Map<string, ClientPerson>();
        for (const p of [...lRes.content, ...sRes.content]) {
          if (p.active && !p.archivedAt) lawMap.set(p.id, p);
        }
        setLawyersPick([...lawMap.values()]);
      } catch {
        if (!cancelled) setLawyersPick([]);
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
        const res = await disputesApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch.trim() || undefined,
          status: statusF || undefined,
          type: typeF || undefined,
          currentStep: stepF || undefined,
          ...(lawyerF.trim() ? { assignedLawyerId: lawyerF.trim() } : {}),
        });
        if (cancelled) return;
        setItems(res.content);
        setTotal(res.totalElements);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل النزاعات");
        }
        setItems([]);
        setTotal(0);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, fetchId, debouncedSearch, statusF, typeF, stepF, lawyerF]);

  const STATUS_OPTS: DisputeStatusDto[] = [
    "NEW",
    "UNDER_REVIEW",
    "WAITING_DOCUMENTS",
    "FRIENDLY_SETTLEMENT",
    "NEGOTIATION",
    "MEDIATION",
    "READY_TO_CONVERT",
    "CONVERTED_TO_CASE",
    "CLOSED",
    "REJECTED",
  ];

  const TYPE_OPTS: DisputeTypeDto[] = [
    "CIVIL",
    "COMMERCIAL",
    "LABOR",
    "ADMINISTRATIVE",
    "CRIMINAL",
    "FAMILY",
    "REAL_ESTATE",
    "OTHER",
  ];

  const STEP_OPTS: DisputeWorkflowStepDto[] = [
    "CREATED",
    "INITIAL_REVIEW",
    "DOCUMENT_COLLECTION",
    "NEGOTIATION",
    "MEDIATION",
    "LEGAL_NOTICE",
    "COURT_FILING",
    "HEARINGS",
    "JUDGMENT",
    "CLOSED",
  ];

  const hasActiveFilters =
    !!statusF || !!typeF || !!stepF || !!lawyerF.trim() || !!search.trim();

  function clearFilters() {
    setStatusF("");
    setTypeF("");
    setStepF("");
    setLawyerF("");
    setSearch("");
    setPage(0);
  }

  return (
    <div dir="rtl" className="space-y-6">
      <DisputeModuleHero
        eyebrow="إدارة ما قبل التقاضي"
        title="النزاعات"
        description="تتبع مسار النزاع قبل وبعد التصعيد إلى قضية رسمية — مع فلاتر للحالة والمرحلة والمحامي."
        icon={Handshake}
        badges={
          !loading && !error ?
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {total.toLocaleString("ar-EG")} نزاع مسجّل
            </span>
          : null
        }
        actions={
          <Button
            variant="outline"
            asChild
            className="border-white/25 bg-[var(--color-gold-400)] text-[var(--color-navy-950)] hover:bg-[var(--color-gold-300)] hover:text-[var(--color-navy-950)]"
          >
            <Link href="/disputes/new" className="inline-flex items-center gap-2">
              <Plus className="size-4" />
              إضافة نزاع
            </Link>
          </Button>
        }
      />

      <SectionCard title="البحث والتصفية" contentClassName="space-y-4">
          <div className="min-w-0">
            <SearchInput
              value={search}
              onChange={(v) => {
                setSearch(v);
                setPage(0);
              }}
              placeholder="بحث برقم النزاع، العنوان، الخصم، العميل، أو اسم المنشأة…"
            />
          </div>

          <div className="border-t border-[var(--color-border)] pt-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2 text-sm font-medium text-[var(--color-foreground)]">
                <SlidersHorizontal className="size-4 text-[var(--color-muted-foreground)]" />
                <span>تصفية النتائج</span>
              </div>
              {hasActiveFilters ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="h-8 gap-1.5 text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
                  onClick={clearFilters}
                >
                  <RotateCcw className="size-3.5" />
                  مسح التصفية
                </Button>
              ) : null}
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div className="min-w-0 space-y-1.5">
                <span className="block text-xs font-medium text-[var(--color-muted-foreground)]">
                  الحالة
                </span>
                <select
                  className={selectFilterClassName}
                  value={statusF}
                  onChange={(e) => {
                    setStatusF((e.target.value || "") as DisputeStatusDto | "");
                    setPage(0);
                  }}
                  aria-label="تصفية الحالة"
                >
                  <option value="">الكل</option>
                  {STATUS_OPTS.map((s) => (
                    <option key={s} value={s}>
                      {DISPUTE_STATUS_AR[s]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="min-w-0 space-y-1.5">
                <span className="block text-xs font-medium text-[var(--color-muted-foreground)]">
                  نوع النزاع
                </span>
                <select
                  className={selectFilterClassName}
                  value={typeF}
                  onChange={(e) => {
                    setTypeF((e.target.value || "") as DisputeTypeDto | "");
                    setPage(0);
                  }}
                  aria-label="تصفية النوع"
                >
                  <option value="">الكل</option>
                  {TYPE_OPTS.map((t) => (
                    <option key={t} value={t}>
                      {DISPUTE_TYPE_AR[t]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="min-w-0 space-y-1.5">
                <span className="block text-xs font-medium text-[var(--color-muted-foreground)]">
                  مرحلة سير العمل
                </span>
                <select
                  className={selectFilterClassName}
                  value={stepF}
                  onChange={(e) => {
                    setStepF((e.target.value || "") as DisputeWorkflowStepDto | "");
                    setPage(0);
                  }}
                  aria-label="تصفية مرحلة سير العمل"
                >
                  <option value="">الكل</option>
                  {STEP_OPTS.map((st) => (
                    <option key={st} value={st}>
                      {DISPUTE_WORKFLOW_STEP_AR[st]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="min-w-0 space-y-1.5">
                <span className="block text-xs font-medium text-[var(--color-muted-foreground)]">
                  المحامي المكلف
                </span>
                <select
                  className={selectFilterClassName}
                  value={lawyerF}
                  onChange={(e) => {
                    setLawyerF(e.target.value);
                    setPage(0);
                  }}
                  aria-label="تصفية بحسب المحامي المكلف"
                >
                  <option value="">الكل</option>
                  {lawyersPick.map((lw) => (
                    <option key={lw.id} value={lw.id}>
                      {lawyerPickerLabel(lw)}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>
      </SectionCard>

      <SectionCard flush>
        {error ? (
          <div className="p-5">
            <ApiErrorAlert
              message={error}
              onRetry={() => setFetchId((n) => n + 1)}
            />
          </div>
        ) : loading ? (
          <PageLoading label="جارٍ تحميل النزاعات…" />
        ) : items.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={Handshake}
              title="لا توجد نزاعات"
              description="ابدأ بإضافة نزاع جديد لتتبع المسار قبل تحويله إلى قضية."
              action={
                <Button variant="gold" asChild>
                  <Link href="/disputes/new">إضافة نزاع</Link>
                </Button>
              }
            />
          </div>
        ) : (
          <>
            <div className="hidden md:block overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-right">رقم / عنوان</TableHead>
                    <TableHead className="text-right">العميل</TableHead>
                    <TableHead className="text-right">الخصم</TableHead>
                    <TableHead className="text-right">النوع</TableHead>
                    <TableHead className="text-right">الحالة</TableHead>
                    <TableHead className="text-right">المسار</TableHead>
                    <TableHead className="text-right">المرحلة</TableHead>
                    <TableHead className="text-right">الخطورة</TableHead>
                    <TableHead className="text-right">المحامي</TableHead>
                    <TableHead className="text-right">الإنشاء</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((row) => (
                    <TableRow
                      key={row.id}
                      className="transition-colors hover:bg-[var(--color-navy-50)]/40"
                    >
                      <TableCell className="font-medium">
                        <Link
                          href={`/disputes/${row.id}`}
                          className="text-[var(--color-foreground)] hover:text-[var(--color-gold-600)] hover:underline"
                        >
                          <span className="block text-xs text-[var(--color-muted-foreground)]">
                            {disputeDisplayRef(row)}
                          </span>
                          {row.title}
                        </Link>
                      </TableCell>
                      <TableCell>{partyLabel(row.client)}</TableCell>
                      <TableCell>{row.opponentName}</TableCell>
                      <TableCell>
                        <Badge tone="neutral">
                          {DISPUTE_TYPE_AR[row.disputeType]}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge tone={statusTone(row.status)}>
                          {DISPUTE_STATUS_AR[row.status]}
                        </Badge>
                      </TableCell>
                      <TableCell className="max-w-[9rem] truncate text-sm">
                        {row.legalPath
                          ? LEGAL_PATH_AR[row.legalPath]
                          : "—"}
                      </TableCell>
                      <TableCell className="max-w-[10rem] text-sm">
                        {DISPUTE_WORKFLOW_STEP_AR[row.currentStep]}
                      </TableCell>
                      <TableCell>
                        <Badge tone={riskTone(row.riskLevel)}>
                          {RISK_LEVEL_AR[row.riskLevel]}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-sm">
                        {lawyerLabel(row.assignedLawyer)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-[var(--color-muted-foreground)]">
                        {formatDate(row.createdAt)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="space-y-3 p-4 md:hidden">
              {items.map((row) => (
                <Link
                  key={row.id}
                  href={`/disputes/${row.id}`}
                  className="block rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm transition hover:border-[var(--color-gold-400)]/50"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-xs text-[var(--color-muted-foreground)]">
                        {disputeDisplayRef(row)}
                      </p>
                      <p className="font-semibold leading-snug">{row.title}</p>
                      <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
                        الخصم: {row.opponentName}
                      </p>
                    </div>
                    <Badge tone={statusTone(row.status)}>
                      {DISPUTE_STATUS_AR[row.status]}
                    </Badge>
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Badge tone="neutral">{DISPUTE_TYPE_AR[row.disputeType]}</Badge>
                    <Badge tone={riskTone(row.riskLevel)}>
                      {RISK_LEVEL_AR[row.riskLevel]}
                    </Badge>
                  </div>
                  <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
                    المرحلة: {DISPUTE_WORKFLOW_STEP_AR[row.currentStep]}
                    {row.claimAmount != null
                      ? ` · المطالبة: ${moneyAr(row.claimAmount)}`
                      : ""}
                  </p>
                </Link>
              ))}
            </div>

            <div className="border-t border-[var(--color-border)] px-4 py-3">
              <Pagination
                page={page}
                pageSize={PAGE_SIZE}
                total={total}
                onPageChange={setPage}
              />
            </div>
          </>
        )}
      </SectionCard>
    </div>
  );
}

export default function DisputesPage() {
  return (
    <Suspense fallback={<PageLoading label="جارٍ التحميل…" />}>
      <DisputesPageInner />
    </Suspense>
  );
}
