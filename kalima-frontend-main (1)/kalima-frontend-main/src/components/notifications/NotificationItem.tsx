import {
  Bell,
  BriefcaseBusiness,
  FileText,
  Gavel,
  ShieldAlert,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils/cn";
import { formatDateTime, formatRelative } from "@/lib/utils/format-date";
import type { AppNotification, NotificationKind } from "@/types/notification";

const KIND_META: Record<
  NotificationKind,
  { icon: React.ComponentType<{ className?: string }>; accent: string }
> = {
  HEARING: {
    icon: Gavel,
    accent: "bg-[var(--color-gold-50)] text-[var(--color-gold-600)]",
  },
  TASK: {
    icon: BriefcaseBusiness,
    accent: "bg-[var(--color-navy-50)] text-[var(--color-navy-700)]",
  },
  INVOICE: {
    icon: FileText,
    accent: "bg-[var(--color-success-bg)] text-[var(--color-success)]",
  },
  CASE_UPDATE: {
    icon: ShieldAlert,
    accent: "bg-[var(--color-info-bg)] text-[var(--color-info)]",
  },
  SYSTEM: {
    icon: Bell,
    accent: "bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
  },
};

export function NotificationItem({
  data,
  onMarkAsRead,
  onRowClick,
  showFullDateTime = false,
  showTypeCode = false,
}: {
  data: AppNotification;
  onMarkAsRead?: (id: string) => void | Promise<void>;
  /** When set with {@link data.href} or a sensible open action, the row is clickable. */
  onRowClick?: (data: AppNotification) => void;
  showFullDateTime?: boolean;
  /** When true, show API enum next to the Arabic type label. */
  showTypeCode?: boolean;
}) {
  const meta = KIND_META[data.kind];
  const Icon = meta.icon;
  const interactive = Boolean(onRowClick && data.href);
  return (
    <li
      role={interactive ? "button" : undefined}
      tabIndex={interactive ? 0 : undefined}
      onClick={
        interactive
          ? () => {
              onRowClick?.(data);
            }
          : undefined
      }
      onKeyDown={
        interactive
          ? (e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onRowClick?.(data);
              }
            }
          : undefined
      }
      className={cn(
        "flex flex-col gap-2 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 transition-colors sm:flex-row sm:items-start",
        !data.read && "ring-1 ring-[var(--color-gold-400)]/40",
        interactive && "cursor-pointer hover:bg-[var(--color-surface-muted)]/50",
      )}
    >
      <div
        className={cn(
          "flex size-10 shrink-0 items-center justify-center rounded-xl",
          meta.accent,
        )}
      >
        <Icon className="size-5" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-col gap-1.5 sm:flex-row sm:items-start sm:justify-between sm:gap-2">
          <div className="min-w-0 space-y-1">
            <h4 className="text-sm font-semibold text-[var(--color-foreground)]">
              {data.title}
            </h4>
            {data.typeLabel ? (
              <div className="flex flex-wrap items-center gap-1.5">
                <Badge tone="navy" className="text-[10px] font-normal ltr-nums">
                  {data.typeLabel}
                </Badge>
                {showTypeCode && data.typeCode ? (
                  <span
                    className="text-[10px] text-[var(--color-muted-foreground)] ltr-nums"
                    title={data.typeCode}
                  >
                    {data.typeCode}
                  </span>
                ) : null}
              </div>
            ) : null}
          </div>
          <div className="shrink-0 text-end sm:max-w-[10rem]">
            <span className="block text-[11px] text-[var(--color-muted-foreground)]">
              {formatRelative(data.createdAt)}
            </span>
            {showFullDateTime ? (
              <span
                className="mt-0.5 block text-[10px] text-[var(--color-muted-foreground)] ltr-nums"
                title={data.createdAt}
              >
                {formatDateTime(data.createdAt)}
              </span>
            ) : null}
          </div>
        </div>
        <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">
          {data.body}
        </p>
        {onMarkAsRead && !data.read ? (
          <Button
            type="button"
            variant="link"
            className="mt-1 h-auto p-0 text-xs text-[var(--color-navy-700)]"
            onClick={(e) => {
              e.stopPropagation();
              void onMarkAsRead(data.id);
            }}
          >
            تمييز كمقروء
          </Button>
        ) : null}
        {interactive && data.href ? (
          <p className="mt-1 text-[10px] text-[var(--color-navy-700)]/80">
            انقر للانتقال إلى الملف
          </p>
        ) : null}
      </div>
      {!data.read ? (
        <span className="mt-0 size-2 shrink-0 self-center rounded-full bg-[var(--color-gold-400)] sm:mt-2" />
      ) : null}
    </li>
  );
}
