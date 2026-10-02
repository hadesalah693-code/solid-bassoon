"use client";

import { useEffect, useState } from "react";
import { Download, ShieldAlert } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Pagination } from "@/components/shared/Pagination";
import { Button } from "@/components/ui/button";
import { AuditLogFilters } from "@/components/audit/AuditLogFilters";
import { AuditLogTable } from "@/components/audit/AuditLogTable";
import { auditApi } from "@/lib/api/audit";
import { ApiRequestError } from "@/lib/api/client";
import { useDebounce } from "@/hooks/use-debounce";
import type { AuditLog } from "@/types/audit";

const PAGE_SIZE = 10;

export default function AuditLogsPage() {
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 300);
  const [status, setStatus] = useState<"all" | "success" | "failed">("all");
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<AuditLog[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await auditApi.list({
          page,
          size: PAGE_SIZE,
          q: debouncedSearch.trim() || undefined,
          success:
            status === "all"
              ? undefined
              : status === "success"
                ? true
                : false,
        });
        if (cancelled) return;
        setItems(res.content);
        setTotal(res.totalElements);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          if (e.status === 403) {
            setError("هذه الصفحة مخصّصة لمسؤول النظام (ADMIN) — GET /api/audit-logs.");
          } else {
            setError(e.details?.message ?? e.message);
          }
        } else {
          setError("تعذر تحميل سجل النشاط");
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
  }, [page, debouncedSearch, status, fetchId]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="سجل النشاط"
        description="عمليات من الخادم — فلترة حسب النجاح والبحث النصي (المعامل q)."
        actions={
          <Button variant="outline" type="button" disabled title="غير متوفر بعد">
            <Download className="size-4" />
            تصدير
          </Button>
        }
      />

      {error ? (
        <ApiErrorAlert
          message={error}
          onRetry={
            error.includes("ADMIN")
              ? undefined
              : () => setFetchId((n) => n + 1)
          }
        />
      ) : (
        <SectionCard flush>
          <div className="p-5">
            <AuditLogFilters
              search={search}
              onSearchChange={(v) => {
                setSearch(v);
                setPage(0);
              }}
              status={status}
              onStatusChange={(v) => {
                setStatus(v);
                setPage(0);
              }}
            />
          </div>
          {loading ? (
            <PageLoading
              label="جارٍ تحميل السجلات…"
              minHeightClassName="min-h-[12rem]"
            />
          ) : items.length === 0 ? (
            <div className="p-6">
              <EmptyState
                title="لا توجد سجلات مطابقة"
                description="عدّل البحث أو فلتر النجاح/الفشل."
                icon={ShieldAlert}
              />
            </div>
          ) : (
            <>
              <AuditLogTable logs={items} />
              <div className="border-t border-[var(--color-border)]">
                <Pagination
                  page={page}
                  pageSize={PAGE_SIZE}
                  total={total}
                  onPageChange={setPage}
                />
              </div>
            </>
          )}
        </SectionCard>
      )}
    </div>
  );
}
