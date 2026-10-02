import Link from "next/link";
import {
  Bell,
  BriefcaseBusiness,
  FileText,
  Gavel,
  ShieldAlert,
} from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";
import { formatRelative } from "@/lib/utils/format-date";
import type { AppNotification, NotificationKind } from "@/types/notification";

const KIND_ICON: Record<NotificationKind, React.ComponentType<{ className?: string }>> = {
  HEARING: Gavel,
  TASK: BriefcaseBusiness,
  INVOICE: FileText,
  CASE_UPDATE: ShieldAlert,
  SYSTEM: Bell,
};

export function NotificationsPreview({
  notifications,
  onMarkRead,
  onReadAll,
}: {
  notifications: AppNotification[];
  /** Mark one as read (e.g. after row click). */
  onMarkRead?: (id: string) => void | Promise<void>;
  /** Optional quick action when there are unread items. */
  onReadAll?: () => void | Promise<void>;
}) {
  return (
    <SectionCard
      className="border-[var(--color-border-strong)]/25 shadow-[var(--shadow-soft)]"
      title="ملخص الإشعارات"
      description="تنبيهات حديثة من مكتبك"
      action={
        <div className="flex flex-wrap items-center gap-1">
          {onReadAll ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => void onReadAll()}
            >
              تعيين الكل
            </Button>
          ) : null}
          <Button variant="ghost" size="sm" asChild>
            <Link href="/notifications">عرض الكل</Link>
          </Button>
        </div>
      }
      flush
    >
      {notifications.length === 0 ? (
        <div className="px-6 py-10 text-center text-sm leading-relaxed text-[var(--color-muted-foreground)]">
          لا إشعارات في آخر تحميل. يمكنك مراجعة صفحة الإشعارات للاطلاع على الأرشيف.
        </div>
      ) : (
      <ul className="divide-y divide-[var(--color-border)]">
        {notifications.map((n) => {
          const Icon = KIND_ICON[n.kind];
          return (
            <li
              key={n.id}
              className={cn(
                "flex items-start gap-3 px-6 py-4 transition-colors hover:bg-[var(--color-surface-muted)]/50",
                !n.read && "bg-[var(--color-gold-50)]/40",
                onMarkRead && !n.read && "cursor-pointer",
              )}
              role={onMarkRead && !n.read ? "button" : undefined}
              onClick={() => {
                if (onMarkRead && !n.read) {
                  void onMarkRead(n.id);
                }
              }}
            >
              <div className="mt-0.5 flex size-9 items-center justify-center rounded-lg bg-[var(--color-surface-muted)] text-[var(--color-navy-700)]">
                <Icon className="size-4" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-[var(--color-foreground)]">
                  {n.href ? (
                    <Link
                      href={n.href}
                      className="hover:underline"
                      onClick={(e) => e.stopPropagation()}
                    >
                      {n.title}
                    </Link>
                  ) : (
                    n.title
                  )}
                </p>
                <p className="mt-0.5 line-clamp-2 text-xs text-[var(--color-muted-foreground)]">
                  {n.body}
                </p>
                <p className="mt-1 text-[11px] text-[var(--color-muted-foreground)]">
                  {formatRelative(n.createdAt)}
                </p>
              </div>
              {!n.read ? (
                <span
                  className="mt-2 size-2 shrink-0 rounded-full bg-[var(--color-gold-400)]"
                  aria-label="غير مقروء"
                />
              ) : null}
            </li>
          );
        })}
      </ul>
      )}
    </SectionCard>
  );
}
