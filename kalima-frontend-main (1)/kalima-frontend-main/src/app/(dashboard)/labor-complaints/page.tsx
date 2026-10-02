"use client";



import { Suspense, useEffect, useMemo, useState } from "react";

import Link from "next/link";

import { HardHat, Plus, RotateCcw } from "lucide-react";

import { useSearchParams } from "next/navigation";



import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";

import { OperationalKpiCard } from "@/components/dashboard/OperationalKpiCard";

import { LaborComplaintStatusFilter } from "@/components/labor-complaints/LaborComplaintStatusFilter";

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

import { laborComplaintsApi } from "@/lib/api/labor-complaints";

import { ApiRequestError } from "@/lib/api/client";

import {

  LABOR_COMPLAINT_STATUS_AR,

  LABOR_COMPLAINT_STATUS_TONE,

} from "@/lib/labor-complaints/labels";

import { useDebounce } from "@/hooks/use-debounce";

import { formatDate } from "@/lib/utils/format-date";

import type { LaborComplaintListItemDto, LaborComplaintStatusDto } from "@/types/labor-complaint";



const PAGE_SIZE = 20;



export default function LaborComplaintsPage() {

  return (

    <Suspense fallback={<PageLoading label="جاري تحميل الشكاوى..." />}>

      <LaborComplaintsPageInner />

    </Suspense>

  );

}



function LaborComplaintsPageInner() {

  const searchParams = useSearchParams();

  const initialSearch = searchParams.get("search") ?? "";



  const [rows, setRows] = useState<LaborComplaintListItemDto[]>([]);

  const [total, setTotal] = useState(0);

  const [page, setPage] = useState(0);

  const [loading, setLoading] = useState(true);

  const [statsLoading, setStatsLoading] = useState(true);

  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState(initialSearch);

  const debouncedSearch = useDebounce(search, 350);

  const [status, setStatus] = useState<LaborComplaintStatusDto | "">("");

  const [fetchId, setFetchId] = useState(0);

  const [statusCounts, setStatusCounts] = useState<Record<LaborComplaintStatusDto | "all", number>>({

    all: 0,

    ACTIVE: 0,

    SAVED: 0,

    REFERRAL_TO_COMMITTEE: 0,

  });



  const hasActiveFilters = !!status || !!search.trim();



  useEffect(() => {

    let cancelled = false;

    (async () => {

      setStatsLoading(true);

      try {

        const [allRes, activeRes, savedRes, referralRes] = await Promise.all([

          laborComplaintsApi.list({ page: 0, size: 1 }),

          laborComplaintsApi.list({ page: 0, size: 1, status: "ACTIVE" }),

          laborComplaintsApi.list({ page: 0, size: 1, status: "SAVED" }),

          laborComplaintsApi.list({ page: 0, size: 1, status: "REFERRAL_TO_COMMITTEE" }),

        ]);

        if (!cancelled) {

          setStatusCounts({

            all: allRes.totalElements,

            ACTIVE: activeRes.totalElements,

            SAVED: savedRes.totalElements,

            REFERRAL_TO_COMMITTEE: referralRes.totalElements,

          });

        }

      } catch {

        if (!cancelled) {

          setStatusCounts({ all: 0, ACTIVE: 0, SAVED: 0, REFERRAL_TO_COMMITTEE: 0 });

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

        const res = await laborComplaintsApi.list({

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

          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل الشكاوى");

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

    if (!status) return "إجمالي الشكاوى";

    return LABOR_COMPLAINT_STATUS_AR[status];

  }, [status]);



  function clearFilters() {

    setStatus("");

    setSearch("");

    setPage(0);

  }



  return (

    <div dir="rtl" className="space-y-6">

      <DisputeModuleHero

        icon={HardHat}

        eyebrow="القضاء العمالي"

        title="الشكاوى العمالية"

        description="متابعة الشكاوى في إدارة حماية الأجور — من التسجيل حتى الحفظ أو الإحالة للجنة فض النزاعات."

        badges={

          !loading && !error ? (

            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">

              {total.toLocaleString("ar-EG")} شكوى {status ? "مطابقة" : "مسجّلة"}

            </span>

          ) : null

        }

        actions={

          <Button

            asChild

            className="border-white/25 bg-[var(--color-gold-400)] text-[var(--color-navy-950)] hover:bg-[var(--color-gold-300)]"

          >

            <Link href="/labor-complaints/new">

              <Plus className="size-4" />

              شكوى جديدة

            </Link>

          </Button>

        }

      />



      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">

        <OperationalKpiCard

          label="إجمالي الشكاوى"

          value={statusCounts.all}

          hint="كل الملفات المسجّلة"

          icon={<HardHat aria-hidden />}

          accent="navy"

          valueLoading={statsLoading}

        />

        <OperationalKpiCard

          label="متداولة"

          value={statusCounts.ACTIVE}

          hint="قيد المتابعة في إدارة العمل"

          icon={<HardHat aria-hidden />}

          accent="gold"

          valueLoading={statsLoading}

        />

        <OperationalKpiCard

          label="محفوظة"

          value={statusCounts.SAVED}

          hint="تم فض النزاع"

          icon={<HardHat aria-hidden />}

          accent="success"

          valueLoading={statsLoading}

        />

        <OperationalKpiCard

          label="محالة للجنة"

          value={statusCounts.REFERRAL_TO_COMMITTEE}

          hint="تصعيد للجنة فض النزاعات"

          icon={<HardHat aria-hidden />}

          accent="info"

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

          placeholder="بحث برقم الشكوى أو الموضوع أو الموكل أو الخصم..."

        />

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--color-border)] pt-4">

          <LaborComplaintStatusFilter

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

          <PageLoading label="جاري تحميل الشكاوى..." />

        ) : rows.length === 0 ? (

          <div className="p-6">

            <EmptyState

              icon={HardHat}

              title={hasActiveFilters ? "لا توجد نتائج" : "لا توجد شكاوى"}

              description={

                hasActiveFilters

                  ? "جرّب تعديل البحث أو إزالة التصفية."

                  : "ابدأ بتسجيل شكوى عمالية جديدة."

              }

              action={

                hasActiveFilters ? (

                  <Button variant="outline" onClick={clearFilters}>

                    مسح التصفية

                  </Button>

                ) : (

                  <Button asChild>

                    <Link href="/labor-complaints/new">شكوى جديدة</Link>

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

                    <TableHead className="text-right">رقم الشكوى</TableHead>

                    <TableHead className="text-right">الموضوع</TableHead>

                    <TableHead className="text-right">الموكل</TableHead>

                    <TableHead className="text-right">الخصم</TableHead>

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

                          href={`/labor-complaints/${row.id}`}

                          className="text-[var(--color-foreground)] hover:text-[var(--color-gold-600)] hover:underline"

                        >

                          <span className="block font-semibold ltr-nums">{row.complaintNumber}</span>

                          {row.internalReference ? (

                            <span className="text-xs text-[var(--color-muted-foreground)]">

                              {row.internalReference}

                            </span>

                          ) : null}

                        </Link>

                      </TableCell>

                      <TableCell className="max-w-[220px] truncate">

                        {row.subject ?? "—"}

                      </TableCell>

                      <TableCell>{row.clientNames ?? row.clientName ?? "—"}</TableCell>

                      <TableCell>{row.opponentNames ?? row.opponentName ?? "—"}</TableCell>

                      <TableCell className="text-sm">

                        {row.assignedLawyerName ?? "—"}

                      </TableCell>

                      <TableCell>

                        <Badge tone={LABOR_COMPLAINT_STATUS_TONE[row.status]}>

                          {LABOR_COMPLAINT_STATUS_AR[row.status]}

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

                  href={`/labor-complaints/${row.id}`}

                  className="block rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm transition hover:border-[var(--color-gold-400)]/50"

                >

                  <div className="flex items-start justify-between gap-2">

                    <div className="min-w-0">

                      <p className="font-semibold ltr-nums text-[var(--color-gold-600)]">

                        {row.complaintNumber}

                      </p>

                      <p className="mt-1 line-clamp-2 font-medium leading-snug">

                        {row.subject ?? "بدون موضوع"}

                      </p>

                      <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">

                        الموكل: {row.clientNames ?? row.clientName ?? "—"}

                      </p>

                      <p className="text-sm text-[var(--color-muted-foreground)]">

                        الخصم: {row.opponentNames ?? row.opponentName ?? "—"}

                      </p>

                    </div>

                    <Badge tone={LABOR_COMPLAINT_STATUS_TONE[row.status]}>

                      {LABOR_COMPLAINT_STATUS_AR[row.status]}

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

