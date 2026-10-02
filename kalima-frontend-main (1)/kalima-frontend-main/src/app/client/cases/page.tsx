"use client";

import { useEffect, useState, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { Briefcase } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SearchInput } from "@/components/shared/SearchInput";
import { EmptyState } from "@/components/shared/EmptyState";
import { CaseCard } from "@/components/cases/CaseCard";
import { getStoredUser } from "@/lib/auth/session";
import { ApiRequestError } from "@/lib/api/client";
import { casesApi } from "@/lib/api/cases";
import { caseFileFromDto } from "@/types/api";
import { useDebounce } from "@/hooks/use-debounce";
import type { CaseFile } from "@/types/case";

const CASES_BASE = "/client/cases";

function ClientCasesInner() {
  const searchParams = useSearchParams();
  const qParam = searchParams.get("q") ?? "";
  const [search, setSearch] = useState(qParam);
  const debounced = useDebounce(search, 300);

  useEffect(() => {
    setSearch(qParam);
  }, [qParam]);

  const [items, setItems] = useState<CaseFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  useEffect(() => {
    const u = getStoredUser();
    if (!u?.id) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await casesApi.list({
          clientId: u.id,
          page: 0,
          size: 100,
          search: debounced.trim() || undefined,
        });
        if (cancelled) return;
        setItems(res.content.map(caseFileFromDto));
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل القضايا");
        }
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [debounced, fetchId]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="قضاياي"
        description="القضايا المسجّلة باسمك لدى المكتب."
      />

      {error ? (
        <ApiErrorAlert
          message={error}
          onRetry={() => setFetchId((n) => n + 1)}
        />
      ) : null}

      <SearchInput
        value={search}
        onChange={setSearch}
        placeholder="ابحث برقم الملف أو العنوان…"
        className="max-w-md"
      />

      {loading ? (
        <PageLoading
          label="جارٍ تحميل القضايا…"
          minHeightClassName="min-h-[16rem]"
        />
      ) : error ? null : items.length === 0 ? (
        <EmptyState
          title={search.trim() ? "لا نتائج" : "لا قضايا"}
          description={
            search.trim()
              ? "جرّب تعديل نص البحث."
              : "عند تسجيل قضايا باسمك ستظهر هنا."
          }
          icon={Briefcase}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {items.map((c) => (
            <CaseCard key={c.id} data={c} casesBasePath={CASES_BASE} />
          ))}
        </div>
      )}

      <p className="text-xs text-[var(--color-muted-foreground)]">
        للاستفسار تواصل مع مكتبك — هذا العرض للاطّلاع فقط.
      </p>
    </div>
  );
}

export default function ClientCasesPage() {
  return (
    <Suspense
      fallback={
        <PageLoading
          label="جارٍ التحميل…"
          minHeightClassName="min-h-[12rem]"
          className="w-full"
        />
      }
    >
      <ClientCasesInner />
    </Suspense>
  );
}
