"use client";



import { useCallback, useEffect, useState } from "react";

import Link from "next/link";

import { MoreVertical, Search, X } from "lucide-react";



import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";

import { EmptyState } from "@/components/shared/EmptyState";

import { PageLoading } from "@/components/shared/PageLoading";

import { CaseStatusBadge } from "@/components/shared/StatusBadge";

import { SectionCard } from "@/components/shared/SectionCard";

import { Button } from "@/components/ui/button";

import {

  DropdownMenu,

  DropdownMenuContent,

  DropdownMenuItem,

  DropdownMenuSeparator,

  DropdownMenuTrigger,

} from "@/components/ui/dropdown-menu";

import { Input } from "@/components/ui/input";

import { delegationsApi } from "@/lib/api/delegations";

import { casesApi } from "@/lib/api/cases";

import { ApiRequestError } from "@/lib/api/client";

import { notifyApiError, notifySuccess } from "@/lib/feedback";

import type { CaseResponseDto } from "@/types/api";

import type { DelegationCaseLinkDto } from "@/types/delegation";



type Props = {

  delegationId: string;

  canEdit?: boolean;

  compact?: boolean;

};



type EnrichedLink = DelegationCaseLinkDto & {

  caseDetails: CaseResponseDto | null;

};



export function DelegationLinkedCasesPanel({

  delegationId,

  canEdit = true,

  compact = false,

}: Props) {

  const [links, setLinks] = useState<EnrichedLink[]>([]);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);

  const [search, setSearch] = useState("");

  const [results, setResults] = useState<CaseResponseDto[]>([]);

  const [searching, setSearching] = useState(false);

  const [linking, setLinking] = useState(false);

  const [unlinkingId, setUnlinkingId] = useState<string | null>(null);



  const load = useCallback(async () => {

    setLoading(true);

    setError(null);

    try {

      const raw = await delegationsApi.listCases(delegationId);

      const enriched = await Promise.all(

        raw.map(async (link) => {

          try {

            const caseDetails = await casesApi.get(link.caseId);

            return { ...link, caseDetails };

          } catch {

            return { ...link, caseDetails: null };

          }

        }),

      );

      setLinks(enriched);

    } catch (e) {

      setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل القضايا");

    } finally {

      setLoading(false);

    }

  }, [delegationId]);



  useEffect(() => {

    void load();

  }, [load]);



  useEffect(() => {

    if (!dialogOpen) return;

    let cancelled = false;

    const t = window.setTimeout(async () => {

      setSearching(true);

      try {

        const res = await casesApi.list({

          search: search.trim() || undefined,

          size: 15,

        });

        if (!cancelled) {

          const linked = new Set(links.map((l) => l.caseId));

          setResults(res.content.filter((c) => !linked.has(c.id)));

        }

      } catch {

        if (!cancelled) setResults([]);

      } finally {

        if (!cancelled) setSearching(false);

      }

    }, 320);

    return () => {

      cancelled = true;

      window.clearTimeout(t);

    };

  }, [dialogOpen, search, links]);



  async function handleLink(caseId: string) {

    setLinking(true);

    try {

      await delegationsApi.linkCase(delegationId, caseId);

      notifySuccess("تم ربط القضية");

      setDialogOpen(false);

      setSearch("");

      await load();

    } catch (e) {

      notifyApiError(e, "تعذّر ربط القضية");

    } finally {

      setLinking(false);

    }

  }



  async function handleUnlink(caseId: string) {

    if (!window.confirm("إلغاء ربط هذه القضية؟")) return;

    setUnlinkingId(caseId);

    try {

      await delegationsApi.unlinkCase(delegationId, caseId);

      notifySuccess("تم إلغاء الربط");

      await load();

    } catch (e) {

      notifyApiError(e, "تعذّر إلغاء الربط");

    } finally {

      setUnlinkingId(null);

    }

  }



  const content = (

    <>

      {error ? <ApiErrorAlert message={error} className="mb-4" /> : null}



      {loading ?

        <PageLoading label="جاري تحميل القضايا..." minHeightClassName="min-h-[8rem]" />

      : links.length === 0 ?

        <EmptyState

          title="لا توجد قضايا مرتبطة"

          description="اربط القضايا التي يُستخدم فيها هذا التوكيل."

        />

      : <div className="overflow-x-auto rounded-xl border border-[var(--color-border)]">

          <table className="w-full min-w-[640px] text-sm">

            <thead>

              <tr className="border-b border-[var(--color-border)] bg-[var(--color-surface-muted)]/50 text-start text-xs text-[var(--color-muted-foreground)]">

                <th className="px-3 py-2.5 font-medium">رقم القضية</th>

                <th className="px-3 py-2.5 font-medium">العنوان</th>

                <th className="px-3 py-2.5 font-medium">المحامي</th>

                <th className="px-3 py-2.5 font-medium">المرحلة</th>

                <th className="px-3 py-2.5 font-medium">الحالة</th>

                <th className="px-3 py-2.5 font-medium">إجراءات</th>

              </tr>

            </thead>

            <tbody className="divide-y divide-[var(--color-border)]">

              {links.map((link) => (

                <tr key={link.id} className="hover:bg-[var(--color-surface-muted)]/30">

                  <td className="px-3 py-3 font-mono text-xs ltr-nums">{link.caseNumber}</td>

                  <td className="max-w-[12rem] truncate px-3 py-3 font-medium">{link.caseTitle}</td>

                  <td className="px-3 py-3 text-[var(--color-muted-foreground)]">

                    {link.caseDetails?.assignedLawyer?.fullName ?? "—"}

                  </td>

                  <td className="px-3 py-3 text-[var(--color-muted-foreground)]">

                    {link.caseDetails?.procedureStage ?? "—"}

                  </td>

                  <td className="px-3 py-3">

                    {link.caseDetails?.status ?

                      <CaseStatusBadge value={link.caseDetails.status} />

                    : "—"}

                  </td>

                  <td className="px-3 py-3">

                    <div className="flex items-center gap-1">

                      <Button size="sm" variant="outline" asChild>

                        <Link href={`/cases/${link.caseId}`}>فتح</Link>

                      </Button>

                      {canEdit ?

                        <DropdownMenu>

                          <DropdownMenuTrigger asChild>

                            <Button type="button" variant="ghost" size="icon" className="size-8" aria-label="المزيد">

                              <MoreVertical className="size-4" />

                            </Button>

                          </DropdownMenuTrigger>

                          <DropdownMenuContent align="end">

                            <DropdownMenuItem asChild>

                              <Link href={`/cases/${link.caseId}`}>عرض القضية</Link>

                            </DropdownMenuItem>

                            <DropdownMenuSeparator />

                            <DropdownMenuItem

                              className="text-[var(--color-danger)]"

                              disabled={unlinkingId === link.caseId}

                              onClick={() => void handleUnlink(link.caseId)}

                            >

                              {unlinkingId === link.caseId ? "جاري الإلغاء..." : "إلغاء الربط"}

                            </DropdownMenuItem>

                          </DropdownMenuContent>

                        </DropdownMenu>

                      : null}

                    </div>

                  </td>

                </tr>

              ))}

            </tbody>

          </table>

        </div>}



      {dialogOpen ?

        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">

          <div className="w-full max-w-lg rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-pop)]">

            <div className="mb-4 flex items-center justify-between">

              <h3 className="font-semibold">ربط قضية</h3>

              <button type="button" aria-label="إغلاق" onClick={() => setDialogOpen(false)}>

                <X className="size-5" />

              </button>

            </div>

            <div className="relative mb-3">

              <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-muted-foreground)]" />

              <Input

                className="ps-10"

                placeholder="بحث برقم أو عنوان القضية..."

                value={search}

                onChange={(e) => setSearch(e.target.value)}

              />

            </div>

            <ul className="max-h-64 space-y-2 overflow-y-auto">

              {searching ?

                <li className="py-4 text-center text-sm text-[var(--color-muted-foreground)]">جاري البحث...</li>

              : results.length === 0 ?

                <li className="py-4 text-center text-sm text-[var(--color-muted-foreground)]">لا توجد نتائج</li>

              : results.map((c) => (

                  <li key={c.id}>

                    <button

                      type="button"

                      disabled={linking}

                      className="w-full rounded-xl border border-[var(--color-border)] px-3 py-2 text-start text-sm hover:border-[var(--color-gold-400)]"

                      onClick={() => void handleLink(c.id)}

                    >

                      <span className="font-medium">{c.title}</span>

                      <span className="mt-0.5 block font-mono text-xs text-[var(--color-muted-foreground)]">

                        {c.caseNumber}

                      </span>

                    </button>

                  </li>

                ))}

            </ul>

          </div>

        </div>

      : null}

    </>

  );



  if (compact) {

    return (

      <SectionCard

        title="القضايا المرتبطة"

        action={

          canEdit ?

            <Button type="button" size="sm" variant="outline" onClick={() => setDialogOpen(true)}>

              ربط قضية

            </Button>

          : null

        }

        contentClassName="space-y-0"

      >

        {content}

      </SectionCard>

    );

  }



  return (

    <SectionCard

      title="القضايا المرتبطة"

      action={

        canEdit ?

          <Button type="button" size="sm" variant="outline" onClick={() => setDialogOpen(true)}>

            ربط قضية

          </Button>

        : null

      }

    >

      {content}

    </SectionCard>

  );

}

