"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Plus, Search } from "lucide-react";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { memosApi } from "@/lib/api/memos";
import { ApiRequestError } from "@/lib/api/client";
import type { MemoResponse, MemoStatus } from "@/types/memo";

const STATUS_AR: Record<MemoStatus, string> = { DRAFT: "مسودة", UNDER_REVIEW: "قيد المراجعة", APPROVED: "معتمدة", SENT: "مرسلة", ARCHIVED: "مؤرشفة" };

export default function MemosPage() {
  const [items, setItems] = useState<MemoResponse[]>([]);
  const [status, setStatus] = useState<MemoStatus | "">("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await memosApi.list({ status: status || undefined, search: search.trim() || undefined, page, size: 20 });
      setItems(result.content);
      setTotalPages(result.totalPages);
      setError(null);
    } catch (e) { setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل المذكرات"); }
    finally { setLoading(false); }
  }, [page, search, status]);
  useEffect(() => { void load(); }, [load]);
  return <div className="space-y-5">
    <PageHeader title="المذكرات" description="مسودات المذكرات القانونية ومراجعتها." actions={<Button asChild><Link href="/memos/new"><Plus className="size-4" /> مذكرة جديدة</Link></Button>} />
    <div className="flex flex-wrap gap-3">
      <label className="relative min-w-56 flex-1"><Search className="absolute end-3 top-3 size-4 text-[var(--color-muted-foreground)]" /><Input className="pe-9" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} placeholder="البحث في العنوان والمحتوى" /></label>
      <select className="h-10 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm" value={status} onChange={(e) => { setStatus(e.target.value as MemoStatus | ""); setPage(0); }}><option value="">كل الحالات</option>{Object.entries(STATUS_AR).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select>
    </div>
    {error ? <ApiErrorAlert message={error} onRetry={() => void load()} /> : null}
    {loading ? <PageLoading label="جارٍ تحميل المذكرات..." /> : items.length === 0 ? <EmptyState title="لا توجد مذكرات" description="أنشئ مذكرة مرتبطة بملف قضية." /> : <div className="divide-y divide-[var(--color-border)]">
      {items.map((memo) => <Link key={memo.id} href={`/memos/${memo.id}`} className="flex flex-wrap items-center justify-between gap-3 py-4"><span><span className="block font-semibold">{memo.title}</span><span className="text-xs text-[var(--color-muted-foreground)]">{memo.caseNumber} · {memo.caseTitle} · {memo.author.fullName ?? "—"}</span></span><span className="text-xs font-semibold">{STATUS_AR[memo.status]}</span></Link>)}
    </div>}
    {totalPages > 1 ? <div className="flex items-center justify-between"><Button variant="outline" disabled={page === 0} onClick={() => setPage((v) => v - 1)}>السابق</Button><span className="text-sm">{page + 1} / {totalPages}</span><Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => setPage((v) => v + 1)}>التالي</Button></div> : null}
  </div>;
}