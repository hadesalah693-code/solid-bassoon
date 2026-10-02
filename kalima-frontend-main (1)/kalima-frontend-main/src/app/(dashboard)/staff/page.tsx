"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Plus, Users } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Pagination } from "@/components/shared/Pagination";
import { Button } from "@/components/ui/button";
import {
  StaffFilters,
  type StaffRoleFilter,
} from "@/components/staff/StaffFilters";
import { StaffTable } from "@/components/staff/StaffTable";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import { notifySuccess } from "@/lib/feedback";
import { getStoredUser } from "@/lib/auth/session";
import type { Staff } from "@/types/staff";

const PAGE_SIZE = 10;

export default function StaffPage() {
  const [role, setRole] = useState<StaffRoleFilter>("ALL");
  const [page, setPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [items, setItems] = useState<Staff[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);
  const [isAdmin, setIsAdmin] = useState(false);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);

  useEffect(() => {
    const u = getStoredUser();
    setIsAdmin(u?.systemRole === "ADMIN");
    setCurrentUserId(u?.id ?? null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await staffApi.list({
          page,
          size: PAGE_SIZE,
          role: role === "ALL" ? undefined : role,
        });
        if (cancelled) return;
        setItems(res.content);
        setTotal(res.totalElements);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل قائمة الموظفين");
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
  }, [page, role, refreshKey, fetchId]);

  const onRefresh = useCallback(() => {
    setRefreshKey((k) => k + 1);
  }, []);

  return (
    <div className="space-y-6">
      <PageHeader
        title="الموظفون"
        description="فريق المكتب — محامون، سكرتارية، محاسبون، ومديرون."
        actions={
          isAdmin ? (
            <Button variant="gold" asChild>
              <Link
                href="/staff/new"
                className="inline-flex items-center gap-2"
              >
                <Plus className="size-4" />
                إضافة موظف
              </Link>
            </Button>
          ) : null
        }
      />

      <SectionCard flush>
        <div className="p-5">
          <StaffFilters
            showSearch={false}
            search=""
            onSearchChange={() => {}}
            role={role}
            onRoleChange={(v) => {
              setRole(v);
              setPage(0);
            }}
          />
        </div>
        {error ? (
          <div className="border-t border-[var(--color-border)] p-5">
            <ApiErrorAlert
              message={error}
              onRetry={() => setFetchId((n) => n + 1)}
            />
          </div>
        ) : null}
        {loading ? (
          <div className="border-t border-[var(--color-border)]">
            <PageLoading
              label="جارٍ تحميل الموظفين…"
              minHeightClassName="min-h-[14rem]"
            />
          </div>
        ) : !error && items.length === 0 ? (
          <div className="p-6">
            <EmptyState
              title="لا يوجد نتائج"
              description="جرّب تغيير فلتر الدور. إن استمرت القائمة فارغة وكنت تتوقع بياناتاً، راجع الاتصال بالخادم أو صلاحية عرض الطاقم."
              icon={Users}
            />
          </div>
        ) : !error && items.length > 0 ? (
          <>
            <StaffTable
              staff={items}
              canManage={isAdmin}
              currentUserId={currentUserId}
              onRefresh={onRefresh}
              onNotify={(message) => notifySuccess(message)}
            />
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
