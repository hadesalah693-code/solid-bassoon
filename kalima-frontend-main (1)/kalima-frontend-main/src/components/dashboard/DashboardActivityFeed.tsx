"use client";

import Link from "next/link";
import {
  Bell,
  Briefcase,
  CheckCircle2,
  ClipboardList,
  Gavel,
  MessageCircle,
  XCircle,
} from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { PageLoading } from "@/components/shared/PageLoading";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";
import { formatRelative } from "@/lib/utils/format-date";
import { AUDIT_ACTION_LABELS } from "@/components/audit/audit-actions";
import type { AppNotification, AppNotificationTypeCode } from "@/types/notification";
import type { AuditLog } from "@/types/audit";

type TimelineItem =
  | {
      kind: "notification";
      id: string;
      at: string;
      notification: AppNotification;
    }
  | {
      kind: "audit";
      id: string;
      at: string;
      log: AuditLog;
    };

const NOTIFY_VERB: Partial<Record<AppNotificationTypeCode, string>> = {
  HEARING_ADDED: "تم جدولة جلسة جديدة",
  HEARING_UPDATED: "تم تحديث موعد جلسة",
  CASE_STATUS_CHANGED: "تم تحديث حالة قضية",
  NEW_MESSAGE: "تم استلام رسالة",
};

function sortItems(
  notifications: AppNotification[],
  audit: AuditLog[],
  max: number,
): TimelineItem[] {
  const merged: TimelineItem[] = [
    ...notifications.map((n) => ({
      kind: "notification" as const,
      id: `n-${n.id}`,
      at: n.createdAt,
      notification: n,
    })),
    ...audit.map((log) => ({
      kind: "audit" as const,
      id: `a-${log.id}`,
      at: log.createdAt,
      log,
    })),
  ];
  merged.sort((x, y) => new Date(y.at).getTime() - new Date(x.at).getTime());
  return merged.slice(0, max);
}

function NotifIcon({ n }: { n: AppNotification }) {
  if (n.kind === "HEARING") return <Gavel className="size-4" />;
  if (n.kind === "CASE_UPDATE") return <Briefcase className="size-4" />;
  if (n.kind === "TASK") return <ClipboardList className="size-4" />;
  if (n.kind === "INVOICE") return <Bell className="size-4" />;
  return <MessageCircle className="size-4" />;
}

export function DashboardActivityFeed({
  notifications,
  auditLogs,
  canSeeAudit,
  onMarkRead,
  onReadAll,
  timelineLoading = false,
}: {
  notifications: AppNotification[];
  auditLogs: AuditLog[];
  canSeeAudit: boolean;
  onMarkRead?: (id: string) => void | Promise<void>;
  onReadAll?: () => void | Promise<void>;
  timelineLoading?: boolean;
}) {
  const auditSlice = canSeeAudit ? auditLogs : [];
  const items = sortItems(notifications, auditSlice, 14);

  if (timelineLoading) {
    return (
      <SectionCard
        className="shadow-[var(--shadow-xs)]"
        title="آخر النشاطات"
        description="التنبيهات والأحداث المرصودة."
        action={
          <Button variant="ghost" size="sm" asChild>
            <Link href="/notifications">الإشعارات</Link>
          </Button>
        }
        flush
      >
        <PageLoading
          label="جارٍ تحميل آخر النشاطات…"
          minHeightClassName="min-h-[10rem]"
          aria-label="جارٍ تحميل الشريط الزمني"
        />
      </SectionCard>
    );
  }

  return (
    <SectionCard
      className="shadow-[var(--shadow-xs)]"
      title="آخر النشاطات"
      description="التنبيهات والأحداث المرصودة."
      action={
        <div className="flex flex-wrap items-center gap-1">
          {onReadAll ?
            <Button type="button" variant="ghost" size="sm" onClick={() => void onReadAll()}>
              تصفير غير المقروء
            </Button>
          : null}
          <Button variant="ghost" size="sm" asChild>
            <Link href="/notifications">الإشعارات</Link>
          </Button>
        </div>
      }
      flush
    >
      {items.length === 0 ?
        <div className="px-6 py-10 text-center text-sm text-[var(--color-muted-foreground)]">
          لا أحداث في آخر دفعة.
        </div>
      :
        <div className="relative px-3 py-4 sm:px-5" dir="rtl">
          <span
            aria-hidden
            className="absolute end-[1.375rem] top-4 bottom-4 w-px bg-[var(--color-border)] sm:end-[1.65rem]"
          />
          <ul className="relative z-[1] flex flex-col gap-0">
            {items.map((it) => (
              <li key={it.id} className="relative pe-10 sm:pe-11">
                <span
                  aria-hidden
                  className="absolute end-2 top-3.5 flex size-7 items-center justify-center rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-navy-600)] sm:end-2.5 sm:size-8"
                >
                  {it.kind === "notification" ?
                    <NotifIcon n={it.notification} />
                  : it.log.success ?
                    <CheckCircle2 className="size-3.5 text-[var(--color-success)]" />
                  : <XCircle className="size-3.5 text-[var(--color-danger)]" />}
                </span>

                <div
                  className={cn(
                    "mb-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2.5",
                    it.kind === "notification" &&
                      !it.notification.read &&
                      "border-[var(--color-gold-200)] bg-[var(--color-gold-50)]/30",
                  )}
                >
                  {it.kind === "notification" ?
                    <div>
                      <p className="text-[0.625rem] font-bold text-[var(--color-gold-600)]">
                        {it.notification.typeCode ?
                          (NOTIFY_VERB[it.notification.typeCode] ?? it.notification.typeLabel ?? "حدث تشغيلي")
                        : it.notification.typeLabel ?? "تنبيه"}
                      </p>
                      <p className="mt-0.5 text-[0.8125rem] font-bold leading-snug text-[var(--color-foreground)]">
                        {it.notification.href ?
                          <Link href={it.notification.href} className="hover:underline">
                            {it.notification.title}
                          </Link>
                        : it.notification.title}
                      </p>
                      <p className="mt-0.5 line-clamp-2 text-[0.6875rem] text-[var(--color-muted-foreground)]">
                        {it.notification.body}
                      </p>
                      <div className="mt-1.5 flex flex-wrap items-center gap-2">
                        <span className="text-[0.625rem] text-[var(--color-muted-foreground)]">
                          {formatRelative(it.notification.createdAt)}
                        </span>
                        {onMarkRead && !it.notification.read ?
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="h-6 text-[0.625rem]"
                            onClick={() => void onMarkRead(it.notification.id)}
                          >
                            تأشير كمقروء
                          </Button>
                        : null}
                      </div>
                    </div>
                  : <div>
                      <p className="text-[0.625rem] font-bold text-[var(--color-navy-600)]">
                        سجل تدقيق ·{" "}
                        {AUDIT_ACTION_LABELS[it.log.action] ?? String(it.log.action)}
                      </p>
                      <p className="mt-0.5 text-[0.6875rem] text-[var(--color-muted-foreground)]">
                        {it.log.actorEmail ?? "نظام"}
                      </p>
                      <p className="mt-1 text-[0.625rem] font-mono text-[var(--color-muted-foreground)]">
                        {formatRelative(it.log.createdAt)}
                      </p>
                    </div>
                  }
                </div>
              </li>
            ))}
          </ul>
        </div>
      }
    </SectionCard>
  );
}
