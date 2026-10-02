"use client";

import { useEffect, useMemo, useState } from "react";
import { CheckCheck, BellOff, ChevronLeft, ChevronRight, Loader2 } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { NotificationItem } from "@/components/notifications/NotificationItem";
import { cn } from "@/lib/utils/cn";
import { notificationsApi } from "@/lib/api/notifications";
import { notificationToApp } from "@/lib/notifications/map-app-notification";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { ApiRequestError } from "@/lib/api/client";
import type { AppNotification } from "@/types/notification";

type Filter = "all" | "unread";

const PAGE_SIZE = 20;

export default function NotificationsPage() {
  const [loadVersion, setLoadVersion] = useState(0);
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isFirstPage, setIsFirstPage] = useState(true);
  const [isLastPage, setIsLastPage] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const [readAllPending, setReadAllPending] = useState(false);
  const [unreadTotal, setUnreadTotal] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const [p, unreadRes] = await Promise.all([
          notificationsApi.list({ page, size: PAGE_SIZE }),
          notificationsApi.unreadCount(),
        ]);
        if (cancelled) return;
        setItems(p.content.map((n) => notificationToApp(n)));
        setTotalPages(p.totalPages);
        setTotalElements(p.totalElements);
        setIsFirstPage(p.first);
        setIsLastPage(p.last);
        setUnreadTotal(unreadRes.count);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setLoadError(e.details?.message ?? e.message);
        } else {
          setLoadError("تعذر تحميل الإشعارات");
        }
        setItems([]);
        setTotalPages(0);
        setTotalElements(0);
        setUnreadTotal(0);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loadVersion, page]);

  const filtered = useMemo(
    () => (filter === "unread" ? items.filter((i) => !i.read) : items),
    [items, filter],
  );

  const currentPageDisplay = totalPages === 0 ? 0 : page + 1;
  const showPagination = totalPages > 1;

  const markAllRead = async () => {
    if (unreadTotal === 0) return;
    setReadAllPending(true);
    try {
      await notificationsApi.readAll();
      setItems((prev) => prev.map((i) => ({ ...i, read: true })));
      setUnreadTotal(0);
      notifySuccess("تم تعيين جميع الإشعارات كمقروءة");
    } catch (e) {
      notifyApiError(e, "تعذر تعيين الكل كمقروء");
    } finally {
      setReadAllPending(false);
    }
  };

  const markOneRead = async (id: string) => {
    const wasUnread = items.find((i) => i.id === id && !i.read);
    try {
      await notificationsApi.markRead(id);
      setItems((prev) =>
        prev.map((i) => (i.id === id ? { ...i, read: true } : i)),
      );
      if (wasUnread) {
        setUnreadTotal((c) => Math.max(0, c - 1));
      }
    } catch (e) {
      notifyApiError(e, "تعذر تحديث حالة القراءة");
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="الإشعارات"
        description={
          !loadError
            ? unreadTotal > 0
              ? `لديك ${unreadTotal} إشعارات غير مقروءة (حسب الخادم).`
              : "كل الإشعارات مقروءة."
            : "تعذر تحميل القائمة — يمكنك إعادة المحاولة أدناه."
        }
        actions={
          <Button
            variant="outline"
            disabled={
              Boolean(loadError) || loading || unreadTotal === 0 || readAllPending
            }
            onClick={() => void markAllRead()}
          >
            {readAllPending ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <CheckCheck className="size-4" />
            )}
            تعيين الكل كمقروء
          </Button>
        }
      />

      {loadError ? (
        <ApiErrorAlert
          message={loadError}
          onRetry={() => {
            setPage(0);
            setLoadVersion((v) => v + 1);
          }}
        />
      ) : null}

      {loading ? (
        <PageLoading
          label="جارٍ تحميل الإشعارات…"
          minHeightClassName="min-h-[16rem]"
        />
      ) : null}

      {!loading && !loadError ? (
        <>
          <div className="flex w-fit flex-col gap-2 self-start sm:flex-row sm:items-center sm:gap-3">
            <div className="flex w-fit items-center gap-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-1">
              <TabButton
                active={filter === "all"}
                onClick={() => setFilter("all")}
                label={`الكل (${totalElements})`}
              />
              <TabButton
                active={filter === "unread"}
                onClick={() => setFilter("unread")}
                label={`غير مقروءة (${unreadTotal})`}
              />
            </div>
            {filter === "unread" ? (
              <p className="text-xs text-[var(--color-muted-foreground)]">
                عند «غير مقروءة»: يُصفى المحتوى ضمن الصفحة الحالية فقط.
              </p>
            ) : null}
          </div>

          {filtered.length === 0 ? (
            <EmptyState
              title={
                items.length > 0 && filter === "unread"
                  ? "لا إشعارات غير مقروءة في هذه الصفحة"
                  : "لا توجد إشعارات"
              }
              description={
                items.length > 0 && filter === "unread"
                  ? "جرّب الانتقال لتبويب «الكل» أو لصفحة أخرى."
                  : "سيظهر هنا إشعار عند تغييرات على القضايا أو الجلسات (من الخادم)."
              }
              icon={BellOff}
            />
          ) : (
            <SectionCard flush>
              <ul className="flex flex-col gap-3 p-4">
                {filtered.map((n) => (
                  <NotificationItem
                    key={n.id}
                    data={n}
                    onMarkAsRead={markOneRead}
                  />
                ))}
              </ul>
            </SectionCard>
          )}

          {showPagination ? (
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--color-border)] pt-4">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={loading || isFirstPage}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="gap-1"
              >
                <ChevronRight className="size-4" />
                السابق
              </Button>
              <p className="text-sm text-[var(--color-muted-foreground)] ltr-nums">
                الصفحة {currentPageDisplay} من {totalPages}
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={loading || isLastPage}
                onClick={() => setPage((p) => p + 1)}
                className="gap-1"
              >
                التالي
                <ChevronLeft className="size-4" />
              </Button>
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  label,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "rounded-lg px-3.5 py-1.5 text-xs font-medium transition-colors ltr-nums",
        active
          ? "bg-[var(--color-navy-800)] text-white"
          : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
      )}
    >
      {label}
    </button>
  );
}
