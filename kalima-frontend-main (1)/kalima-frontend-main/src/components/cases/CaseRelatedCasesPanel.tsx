"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowUpRight, Link2 } from "lucide-react";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { CaseStatusBadge } from "@/components/shared/StatusBadge";
import { casesApi } from "@/lib/api/cases";
import { ApiRequestError } from "@/lib/api/client";
import { caseFileFromDto } from "@/types/api";
import type { CaseFile } from "@/types/case";

export function CaseRelatedCasesPanel({ caseId, parentCaseId }: {
  caseId: string;
  parentCaseId: string | null;
}) {
  const [parent, setParent] = useState<CaseFile | null>(null);
  const [children, setChildren] = useState<CaseFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [related, original] = await Promise.all([
        casesApi.relatedCases(caseId),
        parentCaseId ? casesApi.get(parentCaseId) : Promise.resolve(null),
      ]);
      setChildren(related.content.map(caseFileFromDto));
      setParent(original ? caseFileFromDto(original) : null);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل ارتباطات القضية");
    } finally {
      setLoading(false);
    }
  }, [caseId, parentCaseId]);

  useEffect(() => { void load(); }, [load]);
  if (loading) return <PageLoading label="جارٍ تحميل القضايا المرتبطة..." />;
  if (error) return <ApiErrorAlert message={error} onRetry={() => void load()} />;

  const rows = [...(parent ? [parent] : []), ...children];
  if (!rows.length) return <EmptyState title="لا توجد قضايا مرتبطة" description="تظهر هنا القضية الأصلية وملفات الاستئناف المرتبطة بها." icon={Link2} />;

  return (
    <div className="divide-y divide-[var(--color-border)]">
      {parent ? <p className="pb-2 text-xs font-semibold text-[var(--color-muted-foreground)]">القضية الأصلية</p> : null}
      {rows.map((item) => {
        const isParent = item.id === parent?.id;
        return (
          <Link key={item.id} href={`/cases/${item.id}`} className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0">
            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold">{item.title}</span>
              <span className="text-xs text-[var(--color-muted-foreground)]">{isParent ? "القضية الأصلية" : "استئناف مرتبط"} · {item.caseNumber}</span>
            </span>
            <span className="flex items-center gap-2"><CaseStatusBadge value={item.status} /><ArrowUpRight className="size-4" /></span>
          </Link>
        );
      })}
    </div>
  );
}