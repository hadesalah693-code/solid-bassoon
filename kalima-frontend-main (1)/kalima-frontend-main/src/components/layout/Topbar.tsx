"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Bell, CheckCheck, Menu, Search, X } from "lucide-react";

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

interface TopbarProps {
  onToggleSidebar: () => void;
  sidebarOpen: boolean;
}

export function Topbar({ onToggleSidebar, sidebarOpen }: TopbarProps) {
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
      setPreview(p.content.map((n) => notificationToApp(n)));
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

  const [globalQuery, setGlobalQuery] = useState("");
  const [searchFocused, setSearchFocused] = useState(false);

  function runGlobalSearch(target: "cases" | "clients") {
    const t = globalQuery.trim();
    if (!t) {
      if (target === "cases") {
        router.push("/cases");
      } else {
        router.push("/clients");
      }
      return;
    }
    const enc = encodeURIComponent(t);
    if (target === "cases") {
      router.push(`/cases?q=${enc}`);
    } else {
      router.push(`/clients?q=${enc}`);
    }
  }

  return (
    <header className="sticky top-0 z-30 flex h-[3.5rem] items-center gap-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)]/90 px-4 backdrop-blur-xl sm:px-6">
      <Button
        variant="ghost"
        size="icon"
        className="shrink-0 text-[var(--color-muted-foreground)]"
        onClick={onToggleSidebar}
        aria-label={sidebarOpen ? "إخفاء القائمة" : "إظهار القائمة"}
        aria-expanded={sidebarOpen}
      >
        <Menu className="size-5" />
      </Button>

      <form
        role="search"
        className={cn(
          "relative hidden min-w-0 flex-1 transition-all duration-200 md:block",
          searchFocused ? "max-w-lg" : "max-w-md",
        )}
        onSubmit={(e) => {
          e.preventDefault();
          runGlobalSearch("cases");
        }}
      >
        <Search
          className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-muted-foreground)]"
          aria-hidden
        />
        <input
          type="search"
          name="q"
          value={globalQuery}
          onChange={(e) => setGlobalQuery(e.target.value)}
          onFocus={() => setSearchFocused(true)}
          onBlur={() => setSearchFocused(false)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && e.shiftKey) {
              e.preventDefault();
              runGlobalSearch("clients");
            }
          }}
          placeholder="ابحث عن قضية أو عميل…"
          title="Enter — بحث في القضايا. Shift+Enter — بحث في العملاء."
          className={cn(
            "h-9 w-full rounded-xl border bg-[var(--color-surface-muted)]/60 ps-10 pe-9 text-[0.8125rem] placeholder:text-[var(--color-muted-foreground)] transition-[border-color,box-shadow,background] duration-200",
            searchFocused
              ? "border-[var(--color-gold-400)]/40 bg-[var(--color-surface)] shadow-[var(--shadow-xs)] ring-2 ring-[var(--color-gold-400)]/15"
              : "border-[var(--color-border)]",
            "focus-visible:outline-none",
          )}
          autoComplete="off"
          enterKeyHint="search"
        />
        {globalQuery ? (
          <button
            type="button"
            onClick={() => setGlobalQuery("")}
            className="absolute end-3 top-1/2 -translate-y-1/2 rounded-md p-0.5 text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
            aria-label="مسح البحث"
          >
            <X className="size-3.5" />
          </button>
        ) : null}
      </form>

      <div className="ms-auto flex items-center gap-1.5">
        <DropdownMenu
          onOpenChange={(open) => {
            if (open) void refreshNotifications();
          }}
        >
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="relative text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
              aria-label="الإشعارات"
            >
              <Bell className="size-[1.125rem]" />
              {unreadCount > 0 ? (
                <span className="absolute -top-0.5 -end-0.5 flex min-w-[1.1rem] justify-center rounded-full bg-[var(--color-danger)] px-1 text-[9px] font-bold leading-[1.1rem] text-white ring-2 ring-[var(--color-surface)] ltr-nums">
                  {unreadCount > 9 ? "9+" : unreadCount}
                </span>
              ) : null}
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            align="end"
            className="w-[min(22rem,100vw-2rem)] p-0"
          >
            <DropdownMenuLabel className="flex items-center justify-between">
              <span>أحدث الإشعارات</span>
              {unreadCount > 0 ? (
                <span className="rounded-md bg-[var(--color-danger)]/10 px-1.5 py-0.5 text-[9px] font-bold text-[var(--color-danger)] ltr-nums">
                  {unreadCount} جديدة
                </span>
              ) : null}
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            {preview.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-[var(--color-muted-foreground)]">
                لا إشعارات جديدة
              </p>
            ) : (
              <ul className="max-h-72 overflow-y-auto">
                {preview.map((n) => (
                  <li
                    key={n.id}
                    className="border-b border-[var(--color-border-subtle)] last:border-0"
                  >
                    <button
                      type="button"
                      className="flex w-full flex-col items-start gap-1 px-4 py-3 text-end text-sm transition-colors hover:bg-[var(--color-surface-muted)]"
                      onClick={() => {
                        if (!n.read) void markReadAndRefresh(n.id);
                        if (n.href) router.push(n.href);
                      }}
                    >
                      <span className="flex w-full items-center gap-2">
                        {!n.read ? (
                          <span className="size-1.5 shrink-0 rounded-full bg-[var(--color-gold-400)]" />
                        ) : null}
                        <span
                          className={cn(
                            "flex-1 font-semibold text-[var(--color-foreground)]",
                          )}
                        >
                          {n.title}
                        </span>
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
                <Link href="/notifications" className="cursor-pointer">
                  عرض جميع الإشعارات
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
        ) : (
          <span
            className="text-xs text-[var(--color-muted-foreground)]"
            aria-hidden
          >
            …
          </span>
        )}
      </div>
    </header>
  );
}
