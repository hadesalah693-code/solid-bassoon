"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { BellOff, CheckCheck, Loader2 } from "lucide-react";

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
import { getApiErrorMessage } from "@/lib/get-api-error-message";
import type { AppNotification } from "@/types/notification";

const CASES_BASE = "/client/cases";

type Filter = "all" | "unread";

function friendlyLoadError(e: unknown): string {
  if (e instanceof ApiRequestError) {
    if (e.status === 403) {
      return "لا تملك صلاحية عرض الإشعارات (403). إن كنت بحاجة إلى وصول، تواصل مع المكتب.";
    }
    if (e.status === 404) {
      return "المسار غير متوفر (404).";
    }
    if (e.status === 401) {
      return "انتهت الجلسة أو غير مصرّح — سيتم تحويلك لتسجيل الدخول.";
    }
    return getApiErrorMessage(e, "تعذر تحميل الإشعارات");
  }
  return "تعذر تحميل الإشعارات";
}

export default function ClientNotificationsPage() {
  const router = useRouter();
  const [loadVersion, setLoadVersion] = useState(0);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [serverUnread, setServerUnread] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const [readAllPending, setReadAllPending] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const [p, u] = await Promise.all([
          notificationsApi.list({ page: 0, size: 100 }),
          notificationsApi.unreadCount(),
        ]);
        if (cancelled) return;
        setItems(
          p.content.map((n) => notificationToApp(n, { casesBasePath: CASES_BASE })),
        );
        setServerUnread(u.count);
      } catch (e) {
        if (cancelled) return;
        setLoadError(friendlyLoadError(e));
        setItems([]);
        setServerUnread(0);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loadVersion]);

  const listUnread = useMemo(
    () => items.filter((i) => !i.read).length,
    [items],
  );

  const filtered = useMemo(
    () => (filter === "unread" ? items.filter((i) => !i.read) : items),
    [items, filter],
  );

  async function refreshServerUnread() {
    try {
      const u = await notificationsApi.unreadCount();
      setServerUnread(u.count);
    } catch {
      setServerUnread(listUnread);
    }
  }

  const markAllRead = async () => {
    if (listUnread === 0) return;
    setReadAllPending(true);
    try {
      await notificationsApi.readAll();
      setItems((prev) => prev.map((i) => ({ ...i, read: true })));
      await refreshServerUnread();
      notifySuccess("تم تعيين جميع الإشعارات كمقروءة");
    } catch (e) {
      notifyApiError(e, "تعذر تعيين الكل كمقروء");
    } finally {
      setReadAllPending(false);
    }
  };

  const markOneRead = async (id: string) => {
    try {
      await notificationsApi.markRead(id);
      setItems((prev) =>
        prev.map((i) => (i.id === id ? { ...i, read: true } : i)),
      );
      await refreshServerUnread();
    } catch (e) {
      notifyApiError(e, "تعذر تحديث حالة القراءة");
    }
  };

  function openRow(n: AppNotification) {
    if (n.href) {
      router.push(n.href);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="الإشعارات"
        description={
          loadError
            ? "تعذر تحميل القائمة — يمكنك إعادة المحاولة أدناه."
            : loading
              ? "جارٍ المزامنة مع الخادم…"
              : `غير المقروء: ${serverUnread} (حسب الخادم).`
        }
        actions={
          <Button
            variant="outline"
            disabled={
              Boolean(loadError) || loading || listUnread === 0 || readAllPending
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
          onRetry={() => setLoadVersion((v) => v + 1)}
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
          <div className="flex w-fit items-center gap-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-1">
            <TabButton
              active={filter === "all"}
              onClick={() => setFilter("all")}
              label={`الكل (${items.length})`}
            />
            <TabButton
              active={filter === "unread"}
              onClick={() => setFilter("unread")}
              label={`غير مقروءة (${listUnread})`}
            />
          </div>

          {filtered.length === 0 ? (
            <EmptyState
              title={
                items.length > 0 && filter === "unread"
                  ? "لا إشعارات غير مقروءة"
                  : "لا توجد إشعارات"
              }
              description={
                items.length > 0 && filter === "unread"
                  ? "لقد قرأت كل شيء في القائمة. جرّب تبويب «الكل»."
                  : "سيظهر هنا عند تغييرات على قضاياك أو جلساتك من المكتب."
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
                    onRowClick={openRow}
                    showFullDateTime
                    showTypeCode
                  />
                ))}
              </ul>
            </SectionCard>
          )}
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
