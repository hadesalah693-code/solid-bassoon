"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FileSignature, Plus } from "lucide-react";

import { DelegationCard } from "@/components/delegations/DelegationCard";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { delegationsApi } from "@/lib/api/delegations";
import { ApiRequestError } from "@/lib/api/client";
import type { DelegationListItemDto } from "@/types/delegation";

type Props = {
  clientId: string;
};

export function ClientDelegationsPanel({ clientId }: Props) {
  const [rows, setRows] = useState<DelegationListItemDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await delegationsApi.list({ clientId, size: 12 });
        if (!cancelled) setRows(res.content);
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
  }, [clientId]);

  return (
    <SectionCard
      title="التوكيلات"
      description="وكالات التوكيل المسجّلة لهذا العميل."
      action={
        <Button asChild size="sm" variant="outline" className="gap-1.5">
          <Link href={`/delegations/new?clientId=${clientId}`}>
            <Plus className="size-3.5" aria-hidden />
            توكيل جديد
          </Link>
        </Button>
      }
    >
      {error ? <ApiErrorAlert message={error} className="mb-4" /> : null}
      {loading ?
        <PageLoading label="جاري تحميل التوكيلات..." minHeightClassName="min-h-[8rem]" />
      : rows.length === 0 ?
        <EmptyState
          title="لا توجد توكيلات"
          description="سجّل توكيلاً جديداً لهذا العميل."
          icon={FileSignature}
          action={
            <Button asChild size="sm">
              <Link href={`/delegations/new?clientId=${clientId}`}>توكيل جديد</Link>
            </Button>
          }
        />
      : <>
          <div className="grid gap-4 sm:grid-cols-2">
            {rows.map((d) => (
              <DelegationCard key={d.id} delegation={d} />
            ))}
          </div>
          <div className="mt-4 text-center">
            <Button asChild variant="ghost" size="sm">
              <Link href={`/delegations?clientId=${clientId}`}>عرض كل التوكيلات</Link>
            </Button>
          </div>
        </>}
    </SectionCard>
  );
}
