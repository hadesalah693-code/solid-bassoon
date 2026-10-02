"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Bell, CheckCheck, Menu } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuth } from "@/components/auth/auth-provider";
import { notifyApiError } from "@/lib/feedback";
import { notificationsApi } from "@/lib/api/notifications";
import { notificationToApp } from "@/lib/notifications/map-app-notification";
import { formatRelative } from "@/lib/utils/format-date";
import { cn } from "@/lib/utils/cn";
import type { AppNotification } from "@/types/notification";
import { UserMenu } from "./UserMenu";

interface ClientTopbarProps {
  onToggleSidebar: () => void;
  sidebarOpen: boolean;
}

const CASES_BASE = "/client/cases";

export function ClientTopbar({ onToggleSidebar, sidebarOpen }: ClientTopbarProps) {
  const router = useRouter();
  const { user, loading, logout } = useAuth();
  const [preview, setPreview] = useState<AppNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);

  const refreshNotifications = useCallback(async () => {
    try {
      const [p, u] = await Promise.all([
        notificationsApi.list({ page: 0, size: 5 }),
        notificationsApi.unreadCount(),
      ]);
      setPreview(
        p.content.map((n) => notificationToApp(n, { casesBasePath: CASES_BASE })),
      );
      setUnreadCount(u.count);
    } catch {
      setPreview([]);
      setUnreadCount(0);
    }
  }, []);

  useEffect(() => {
    if (loading || !user) return;
    void refreshNotifications();
  }, [loading, user, refreshNotifications]);

  function onLogout() {
    void logout();
  }

  async function markReadAndRefresh(id: string) {
    try {
      await notificationsApi.markRead(id);
      await refreshNotifications();
    } catch (e) {
      notifyApiError(e, "تعذر تحديث حالة القراءة");
    }
  }

  async function readAll() {
    try {
      await notificationsApi.readAll();
      await refreshNotifications();
    } catch (e) {
      notifyApiError(e, "تعذر تعيين الكل كمقروء");
    }
  }

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-[var(--color-border)] bg-[var(--color-surface)]/80 px-4 backdrop-blur-md sm:px-6">
      <Button
        variant="ghost"
        size="icon"
        className="shrink-0"
        onClick={onToggleSidebar}
        aria-label={sidebarOpen ? "إخفاء القائمة" : "إظهار القائمة"}
        aria-expanded={sidebarOpen}
      >
        <Menu className="size-5" />
      </Button>

      <p className="min-w-0 text-sm text-[var(--color-muted-foreground)]">
        متابعة قضاياك والجلسات والتنبيهات
      </p>

      <div className="ms-auto flex items-center gap-2">
        <DropdownMenu
          onOpenChange={(open) => {
            if (open) void refreshNotifications();
          }}
        >
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="relative"
              aria-label="الإشعارات"
            >
              <Bell className="size-5" />
              {unreadCount > 0 ? (
                <span className="absolute -top-0.5 -end-0.5 flex min-w-[1.15rem] justify-center rounded-full bg-[var(--color-danger)] px-1 text-[10px] font-bold leading-4 text-white ring-2 ring-[var(--color-surface)] ltr-nums">
                  {unreadCount > 9 ? "9+" : unreadCount}
                </span>
              ) : null}
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            align="end"
            className="w-[min(20rem,100vw-2rem)] p-0"
          >
            <DropdownMenuLabel className="px-3 py-2 text-sm">
              أحدث الإشعارات
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            {preview.length === 0 ? (
              <p className="px-3 py-4 text-sm leading-relaxed text-[var(--color-muted-foreground)]">
                لا إشعارات جديدة في آخر طلب. يمكنك الاطّلاع على المزيد من
                «الرئيسية».
              </p>
            ) : (
              <ul className="max-h-64 overflow-y-auto">
                {preview.map((n) => (
                  <li
                    key={n.id}
                    className="border-b border-[var(--color-border)] last:border-0"
                  >
                    <button
                      type="button"
                      className="flex w-full flex-col items-start gap-0.5 px-3 py-2.5 text-end text-sm hover:bg-[var(--color-surface-muted)]/70"
                      onClick={() => {
                        if (!n.read) void markReadAndRefresh(n.id);
                        if (n.href) router.push(n.href);
                      }}
                    >
                      <span
                        className={cn(
                          "w-full font-medium text-[var(--color-foreground)]",
                          !n.read && "text-[var(--color-navy-800)]",
                        )}
                      >
                        {n.title}
                      </span>
                      <span className="line-clamp-2 w-full text-xs text-[var(--color-muted-foreground)]">
                        {n.body}
                      </span>
                      <span className="text-[10px] text-[var(--color-muted-foreground)] ltr-nums">
                        {formatRelative(n.createdAt)}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <DropdownMenuSeparator />
            <div className="flex flex-col gap-0.5 p-1">
              <DropdownMenuItem
                className="cursor-pointer"
                onSelect={async (e) => {
                  e.preventDefault();
                  await readAll();
                }}
                disabled={unreadCount === 0}
              >
                <CheckCheck className="size-4" />
                تعيين الكل كمقروء
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link href="/client/notifications" className="cursor-pointer">
                  جميع الإشعارات
                </Link>
              </DropdownMenuItem>
            </div>
          </DropdownMenuContent>
        </DropdownMenu>
        {!loading && user ? (
          <UserMenu user={user} onLogout={onLogout} />
        ) : !loading ? (
          <Button type="button" variant="ghost" size="sm" onClick={onLogout}>
            تسجيل الخروج
          </Button>
        ) : null}
      </div>
    </header>
  );
}
