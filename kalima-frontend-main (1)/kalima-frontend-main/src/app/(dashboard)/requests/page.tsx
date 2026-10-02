"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeftRight, ArrowUpRight } from "lucide-react";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { Button } from "@/components/ui/button";
import { circulationApi } from "@/lib/api/circulation";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import type { CirculationRequest, CirculationStatus } from "@/types/circulation";

const STATUSES: { value: CirculationStatus | ""; label: string }[] = [
  { value: "", label: "الكل" }, { value: "PENDING", label: "معلّق" },
  { value: "APPROVED", label: "معتمد" }, { value: "IN_HAND", label: "بعهدة الموظف" },
  { value: "RETURNED", label: "مُعاد" }, { value: "REJECTED", label: "مرفوض" },
  { value: "CANCELLED", label: "ملغى" },
];
const STATUS_LABEL: Record<CirculationStatus, string> = {
  PENDING: "معلّق", APPROVED: "معتمد", IN_HAND: "بعهدة الموظف", RETURNED: "مُعاد", REJECTED: "مرفوض", CANCELLED: "ملغى",
};

export default function RequestsPage() {
  const [direction, setDirection] = useState<"inbox" | "outbox">("inbox");
  const [status, setStatus] = useState<CirculationStatus | "">("");
  const [items, setItems] = useState<CirculationRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const role = getStoredUser()?.systemRole;
  const canApprove = role === "ADMIN" || role === "SENIOR_LAWYER";
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await circulationApi[direction](status || undefined, page, 20);
      setItems(result.content); setTotalPages(result.totalPages); setError(null);
    } catch (e) { setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل طلبات التداول"); }
    finally { setLoading(false); }
  }, [direction, page, status]);
  useEffect(() => { void load(); }, [load]);
  async function act(id: string, kind: "approve" | "reject" | "handover" | "returnFile" | "cancel") {
    try { await circulationApi[kind](id); notifySuccess("تم تحديث الطلب"); await load(); }
    catch (e) { notifyApiError(e, "تعذر تنفيذ الإجراء"); }
  }
  return <div className="space-y-5">
    <PageHeader title="طلبات تداول الملفات" description="طلبات تسليم ملفات القضايا بين فريق المكتب." actions={<Button asChild><Link href="/requests/new"><ArrowLeftRight className="size-4" /> طلب تداول</Link></Button>} />
    <div className="flex flex-wrap items-center gap-2 border-b border-[var(--color-border)] pb-3">
      <div className="inline-flex rounded-lg border border-[var(--color-border)] p-1" role="tablist">
        <button className={`rounded-md px-3 py-1.5 text-sm ${direction === "inbox" ? "bg-[var(--color-navy-800)] text-white" : ""}`} onClick={() => { setDirection("inbox"); setPage(0); }}>الوارد</button>
        <button className={`rounded-md px-3 py-1.5 text-sm ${direction === "outbox" ? "bg-[var(--color-navy-800)] text-white" : ""}`} onClick={() => { setDirection("outbox"); setPage(0); }}>الصادر</button>
      </div>
      {STATUSES.map((item) => <button key={item.value || "all"} onClick={() => { setStatus(item.value); setPage(0); }} aria-pressed={status === item.value} className={`rounded-full border px-3 py-1.5 text-xs ${status === item.value ? "border-[var(--color-gold-500)] bg-[var(--color-gold-50)]" : "border-[var(--color-border)]"}`}>{item.label}</button>)}
    </div>
    {error ? <ApiErrorAlert message={error} onRetry={() => void load()} /> : null}
    {loading ? <PageLoading label="جارٍ تحميل الطلبات..." /> : items.length === 0 ? <EmptyState title="لا توجد طلبات" description="ستظهر طلبات التداول في هذا العرض." icon={ArrowLeftRight} /> : <div className="divide-y divide-[var(--color-border)]">
      {items.map((item) => <article key={item.id} className="space-y-3 py-4 first:pt-0">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0"><Link href={`/cases/${item.caseId}?section=circulation`} className="font-semibold hover:underline">{item.caseNumber} · {item.caseTitle}<ArrowUpRight className="ms-1 inline size-3.5" /></Link><p className="mt-1 text-sm">{item.fromPerson.fullName} ← {item.toPerson.fullName}</p><p className="mt-1 text-xs text-[var(--color-muted-foreground)]">{item.reason}</p></div>
          <span className="shrink-0 rounded-full border border-[var(--color-border)] px-2.5 py-1 text-xs">{STATUS_LABEL[item.status]}</span>
        </div>
        <div className="flex flex-wrap gap-2">
          {item.status === "PENDING" && direction === "inbox" && canApprove ? <><Button size="sm" onClick={() => void act(item.id, "approve")}>موافقة</Button><Button size="sm" variant="outline" onClick={() => void act(item.id, "reject")}>رفض</Button></> : null}
          {item.status === "APPROVED" ? <Button size="sm" onClick={() => void act(item.id, "handover")}>تسليم الملف</Button> : null}
          {item.status === "IN_HAND" ? <Button size="sm" onClick={() => void act(item.id, "returnFile")}>إعادة الملف</Button> : null}
          {item.status === "PENDING" && direction === "outbox" ? <Button size="sm" variant="outline" onClick={() => void act(item.id, "cancel")}>إلغاء الطلب</Button> : null}
        </div>
      </article>)}
    </div>}
    {totalPages > 1 ? <div className="flex justify-between"><Button variant="outline" disabled={page === 0} onClick={() => setPage((v) => v - 1)}>السابق</Button><span className="self-center text-sm">{page + 1} / {totalPages}</span><Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => setPage((v) => v + 1)}>التالي</Button></div> : null}
  </div>;
}