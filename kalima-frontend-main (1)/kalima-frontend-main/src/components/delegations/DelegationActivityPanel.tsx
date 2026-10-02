"use client";

import { useCallback, useEffect, useState } from "react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { delegationsApi } from "@/lib/api/delegations";
import { ApiRequestError } from "@/lib/api/client";
import { delegationActionAr } from "@/lib/delegations/activity-labels";
import { formatDateTime } from "@/lib/utils/format-date";
import type { DelegationActivityDto } from "@/types/delegation";

type Props = {
  delegationId: string;
};

export function DelegationActivityPanel({ delegationId }: Props) {
  const [rows, setRows] = useState<DelegationActivityDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await delegationsApi.activity(delegationId, 0, 30);
      setRows(res.content);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل السجل");
    } finally {
      setLoading(false);
    }
  }, [delegationId]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <SectionCard title="سجل الحركة">
      {error ? <ApiErrorAlert message={error} className="mb-4" /> : null}
      {loading ?
        <PageLoading label="جاري تحميل السجل..." minHeightClassName="min-h-[8rem]" />
      : rows.length === 0 ?
        <EmptyState title="لا يوجد سجل بعد" description="ستظهر هنا عمليات الإنشاء والتعديل والربط." />
      : <ul className="space-y-3">
          {rows.map((row) => (
            <li
              key={row.id}
              className="rounded-xl border border-[var(--color-border)] px-4 py-3 text-sm"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium">{delegationActionAr(row.action)}</span>
                <span className="text-xs text-[var(--color-muted-foreground)]">
                  {formatDateTime(row.createdAt)}
                </span>
              </div>
              <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-[var(--color-muted-foreground)]">
                {row.actorEmail ? <span>{row.actorEmail}</span> : null}
                <Badge tone={row.success ? "success" : "danger"}>
                  {row.success ? "نجح" : "فشل"}
                </Badge>
              </div>
              {row.metadata ?
                <pre className="mt-2 max-h-24 overflow-auto rounded-lg bg-[var(--color-surface-muted)]/50 p-2 text-[10px] leading-relaxed">
                  {row.metadata}
                </pre>
              : null}
            </li>
          ))}
        </ul>}
    </SectionCard>
  );
}
