import Link from "next/link";
import { CheckCircle2, XCircle } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { formatRelative } from "@/lib/utils/format-date";
import { cn } from "@/lib/utils/cn";
import type { AuditLog } from "@/types/audit";
import { AUDIT_ACTION_LABELS } from "@/components/audit/audit-actions";

export function AuditPreview({
  logs,
  emptyHint,
}: {
  logs: AuditLog[];
  /** Shown when there are no rows (e.g. non-ADMIN or empty log). */
  emptyHint?: string;
}) {
  return (
    <SectionCard
      className="border-[var(--color-border-strong)]/25 shadow-[var(--shadow-soft)]"
      title="آخر النشاطات"
      description="سجل مختصر من التدقيق للمشرف — التفاصيل الكاملة في صفحة السجل."
      action={
        <Button variant="ghost" size="sm" asChild>
          <Link href="/audit-logs">عرض السجل الكامل</Link>
        </Button>
      }
      flush
    >
      {logs.length === 0 ? (
        <div className="rounded-xl bg-[var(--color-surface-muted)]/40 px-6 py-10 text-center text-sm leading-relaxed text-[var(--color-muted-foreground)]">
          {emptyHint ?? "لا سجلات في هذه الفترة."}
        </div>
      ) : (
      <ul className="divide-y divide-[var(--color-border)]">
        {logs.map((log) => (
          <li key={log.id} className="flex items-start gap-3 px-6 py-3.5">
            <div
              className={cn(
                "mt-0.5 flex size-8 items-center justify-center rounded-lg",
                log.success
                  ? "bg-[var(--color-success-bg)] text-[var(--color-success)]"
                  : "bg-[var(--color-danger-bg)] text-[var(--color-danger)]",
              )}
            >
              {log.success ? (
                <CheckCircle2 className="size-4" />
              ) : (
                <XCircle className="size-4" />
              )}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm">
                <span className="font-semibold text-[var(--color-foreground)]">
                  {AUDIT_ACTION_LABELS[log.action] ?? log.action}
                </span>
                {log.actorEmail ? (
                  <span className="text-[var(--color-muted-foreground)]">
                    {" "}
                    — {log.actorEmail}
                  </span>
                ) : null}
              </p>
              <p className="mt-0.5 text-xs text-[var(--color-muted-foreground)]">
                {formatRelative(log.createdAt)}
              </p>
            </div>
          </li>
        ))}
      </ul>
      )}
    </SectionCard>
  );
}
