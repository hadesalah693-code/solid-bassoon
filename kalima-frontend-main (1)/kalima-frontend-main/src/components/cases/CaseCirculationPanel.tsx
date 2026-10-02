"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeftRight, Plus } from "lucide-react";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { Button } from "@/components/ui/button";
import { circulationApi } from "@/lib/api/circulation";
import { ApiRequestError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils/format-date";
import type { CirculationRequest, CirculationStatus } from "@/types/circulation";

const STATUS_AR: Record<CirculationStatus, string> = {
  PENDING: "قيد الانتظار", APPROVED: "معتمد", IN_HAND: "بعهدة الموظف",
  RETURNED: "مُعاد", REJECTED: "مرفوض", CANCELLED: "ملغى",
};

export function CaseCirculationPanel({ caseId, canCreate }: { caseId: string; canCreate: boolean }) {
  const [items, setItems] = useState<CirculationRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await circulationApi.forCase(caseId, 0, 50);
      setItems(page.content);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل سجل التداول");
    } finally { setLoading(false); }
  }, [caseId]);
  useEffect(() => { void load(); }, [load]);
  if (loading) return <PageLoading label="جارٍ تحميل سجل التداول..." />;
  if (error) return <ApiErrorAlert message={error} onRetry={() => void load()} />;
  return <div className="space-y-4">
    {canCreate ? <div className="flex justify-end"><Button asChild><Link href={`/requests/new?caseId=${caseId}`}><Plus className="size-4" /> طلب تداول</Link></Button></div> : null}
    {!items.length ? <EmptyState title="لا توجد طلبات تداول" description="ستظهر هنا حركات تسليم ملف القضية وإعادته." icon={ArrowLeftRight} /> : (
      <div className="divide-y divide-[var(--color-border)]">
        {items.map((item) => <article key={item.id} className="py-4 first:pt-0">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div><p className="font-semibold">{item.fromPerson.fullName ?? "—"} ← {item.toPerson.fullName ?? "—"}</p><p className="mt-1 text-sm">{item.reason}</p><p className="mt-1 text-xs text-[var(--color-muted-foreground)]">طلب بواسطة {item.requestedBy.fullName ?? "—"} · {formatDateTime(item.requestedAt)}</p></div>
            <span className="rounded-full border border-[var(--color-border)] px-2.5 py-1 text-xs">{STATUS_AR[item.status]}</span>
          </div>
          <details className="mt-3 text-sm">
            <summary className="cursor-pointer text-xs font-semibold text-[var(--color-navy-700)]">سجل الحركات ({item.events.length})</summary>
            <ol className="mt-3 space-y-2 border-s border-[var(--color-border)] ps-4">
              {item.events.map((event) => <li key={event.id}><p className="font-medium">{event.eventType} · {event.actorName ?? "—"}</p><p className="text-xs text-[var(--color-muted-foreground)]">{formatDateTime(event.occurredAt)}{event.notes ? ` · ${event.notes}` : ""}</p></li>)}
            </ol>
          </details>
        </article>)}
      </div>
    )}
  </div>;
}