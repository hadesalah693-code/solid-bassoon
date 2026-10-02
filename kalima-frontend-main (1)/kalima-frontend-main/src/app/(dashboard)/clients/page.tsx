"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { UserPlus, Users } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Pagination } from "@/components/shared/Pagination";
import { Button } from "@/components/ui/button";
import { SearchInput } from "@/components/shared/SearchInput";
import { ClientTable } from "@/components/clients/ClientTable";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import { useDebounce } from "@/hooks/use-debounce";
import type { ClientPerson } from "@/types/person";

const PAGE_SIZE = 10;

function ClientsPageInner() {
  const searchParams = useSearchParams();
  const qParam = searchParams.get("q") ?? "";
  const [search, setSearch] = useState(qParam);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebounce(search, 300);

  useEffect(() => {
    setSearch(qParam);
    setPage(0);
  }, [qParam]);
  const [items, setItems] = useState<ClientPerson[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);
  // Backend: POST /api/persons requires [ADMIN, SECRETARY]. We hide the "New
  // client" button for other roles to avoid a guaranteed 403.
  const [canCreate, setCanCreate] = useState(false);
  useEffect(() => {
    const r = getStoredUser()?.systemRole;
    setCanCreate(r === "ADMIN" || r === "SECRETARY");
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await personsApi.list({
          page,
          size: PAGE_SIZE,
          role: "CLIENT",
          search: debouncedSearch.trim() || undefined,
        });
        if (cancelled) return;
        setItems(res.content);
        setTotal(res.totalElements);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل العملاء");
        }
        setItems([]);
        setTotal(0);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, fetchId, debouncedSearch]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="العملاء"
        description="قائمة العملاء (أشخاص بدور «عميل» في النظام)."
        actions={
          canCreate ? (
            <Button variant="gold" asChild>
              <Link
                href="/clients/new"
                className="inline-flex items-center gap-2"
              >
                <UserPlus className="size-4" />
                تسجيل عميل
              </Link>
            </Button>
          ) : null
        }
      />

      <div className="max-w-md">
        <SearchInput
          value={search}
          onChange={(v) => {
            setSearch(v);
            setPage(0);
          }}
          placeholder="ابحث بالاسم، البريد، الهاتف…"
        />
      </div>

      <SectionCard flush>
        {error ? (
          <div className="p-5">
            <ApiErrorAlert
              message={error}
              onRetry={() => setFetchId((n) => n + 1)}
            />
          </div>
        ) : null}
        {loading ? (
          <PageLoading
            label="جارٍ تحميل العملاء…"
            minHeightClassName="min-h-[14rem]"
          />
        ) : !error && items.length === 0 ? (
          <div className="p-6">
            <EmptyState
              title={debouncedSearch.trim() ? "لا نتائج للبحث" : "لا يوجد عملاء"}
              description={
                debouncedSearch.trim()
                  ? "جرّب كلمات أخرى أو أفرغ حقل البحث لعرض القائمة الكاملة."
                  : canCreate
                    ? "لم يُسجّل أي عميل (بدور «عميل») بعد. استخدم الزر أعلاه لإضافة عميل."
                    : "لم يُسجّل أي عميل بعد. إضافة العملاء متاحة للمدير والسكرتارية فقط."
              }
              icon={Users}
            />
          </div>
        ) : !error && items.length > 0 ? (
          <>
            <ClientTable persons={items} />
            <div className="border-t border-[var(--color-border)]">
              <Pagination
                page={page}
                pageSize={PAGE_SIZE}
                total={total}
                onPageChange={setPage}
              />
            </div>
          </>
        ) : null}
      </SectionCard>
    </div>
  );
}

export default function ClientsPage() {
  return (
    <Suspense
      fallback={
        <div className="space-y-6">
          <PageLoading
            label="جارٍ تحميل الصفحة…"
            minHeightClassName="min-h-[14rem]"
            className="w-full"
          />
        </div>
      }
    >
      <ClientsPageInner />
    </Suspense>
  );
}
