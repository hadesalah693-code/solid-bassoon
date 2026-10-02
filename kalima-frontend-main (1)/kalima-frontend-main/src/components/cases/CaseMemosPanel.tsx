"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Plus } from "lucide-react";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { Button } from "@/components/ui/button";
import { memosApi } from "@/lib/api/memos";
import { ApiRequestError } from "@/lib/api/client";
import type { MemoResponse } from "@/types/memo";

const STATUS_AR: Record<MemoResponse["status"], string> = {
  DRAFT: "مسودة", UNDER_REVIEW: "قيد المراجعة", APPROVED: "معتمدة", SENT: "مرسلة", ARCHIVED: "مؤرشفة",
};

export function CaseMemosPanel({ caseId, canEdit }: { caseId: string; canEdit: boolean }) {
  const [memos, setMemos] = useState<MemoResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await memosApi.list({ caseId, size: 50 });
      setMemos(page.content);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل المذكرات");
    } finally { setLoading(false); }
  }, [caseId]);
  useEffect(() => { void load(); }, [load]);
  if (loading) return <PageLoading label="جارٍ تحميل المذكرات..." />;
  if (error) return <ApiErrorAlert message={error} onRetry={() => void load()} />;
  return (
    <div className="space-y-4">
      {canEdit ? <div className="flex justify-end"><Button asChild><Link href={`/memos/new?caseId=${caseId}`}><Plus className="size-4" /> مذكرة جديدة</Link></Button></div> : null}
      {!memos.length ? <EmptyState title="لا توجد مذكرات" description="تظهر مذكرات هذه القضية هنا." /> : (
        <div className="divide-y divide-[var(--color-border)]">
          {memos.map((memo) => <Link key={memo.id} href={`/memos/${memo.id}`} className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0">
            <span><span className="block font-semibold">{memo.title}</span><span className="text-xs text-[var(--color-muted-foreground)]">{memo.caseNumber} · {memo.author.fullName ?? "—"}</span></span>
            <span className="text-xs font-semibold text-[var(--color-navy-700)]">{STATUS_AR[memo.status]}</span>
          </Link>)}
        </div>
      )}
    </div>
  );
}