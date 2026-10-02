"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";
import { formatDateTime } from "@/lib/utils/format-date";
import type { TaskResponseDto } from "@/types/api";
import { TaskRelationBadge } from "@/components/tasks/TaskRelationBadge";

export type DashboardTaskFlat = TaskResponseDto & {
  caseNumber: string;
  caseTitle: string;
};

type DashboardTaskQueueProps = {
  tasks: DashboardTaskFlat[];
};

function urgencyOf(t: DashboardTaskFlat): {
  label: string;
  tone: "danger" | "warning" | "neutral";
  border: boolean;
} {
  const todayKey = new Date().toISOString().slice(0, 10);
  if (!t.dueDate) return { label: "بدون موعد", tone: "neutral", border: false };
  const d = t.dueDate.slice(0, 10);
  if (d < todayKey) return { label: "متأخرة", tone: "danger", border: true };
  if (d === todayKey) return { label: "تستحق اليوم", tone: "warning", border: true };
  return {
    label: formatDateTime(t.dueDate),
    tone: "neutral",
    border: false,
  };
}

export function DashboardTaskQueue({ tasks }: DashboardTaskQueueProps) {
  const top = [...tasks].slice(0, 8);

  return (
    <SectionCard
      className="shadow-[var(--shadow-xs)]"
      title="طابور المهام"
      description="غير المنجزة مرتبة حسب الاستحقاق والأولوية."
      action={
        <Button variant="ghost" size="sm" asChild>
          <Link href="/tasks" className="inline-flex items-center gap-1">
            إدارة المهام
            <ArrowLeft className="size-3.5 opacity-60" aria-hidden />
          </Link>
        </Button>
      }
      flush
    >
      {top.length === 0 ?
        <div className="px-6 py-10 text-center text-sm text-[var(--color-muted-foreground)]">
          لا مهام ظاهرة في هذه العيّنة.
        </div>
      :
        <ul className="divide-y divide-[var(--color-border-subtle)]">
          {top.map((t) => {
            const urg = urgencyOf(t);
            const prio =
              t.priority === "URGENT" ?
                { text: "مستعجلة", tone: "danger" as const }
              : t.priority === "HIGH" ?
                { text: "عالية", tone: "warning" as const }
              : { text: "عادية", tone: "neutral" as const };

            return (
              <li
                key={t.id}
                className={cn(
                  "flex flex-col gap-2 px-5 py-3 sm:flex-row sm:items-center sm:justify-between",
                  urg.border && urg.tone === "danger" && "bg-[var(--color-danger-bg)]/40",
                  urg.border && urg.tone === "warning" && "bg-[var(--color-warning-bg)]/40",
                )}
              >
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate text-[0.8125rem] font-bold text-[var(--color-foreground)]">
                      {t.title}
                    </p>
                    <TaskRelationBadge task={t} />
                  </div>
                  <p className="mt-0.5 text-[0.6875rem] text-[var(--color-muted-foreground)]">
                    {t.caseId && t.caseTitle ? (
                      <>
                        <Link
                          href={`/cases/${t.caseId}`}
                          className="font-semibold text-[var(--color-navy-600)] hover:underline"
                        >
                          {t.caseTitle}
                        </Link>
                        {t.caseNumber ? (
                          <span className="font-mono ltr-nums">
                            {" "}
                            · #{t.caseNumber}
                          </span>
                        ) : null}
                      </>
                    ) : t.disputeId && t.disputeTitle ? (
                      <Link
                        href={`/disputes/${t.disputeId}`}
                        className="font-semibold text-[var(--color-navy-600)] hover:underline"
                      >
                        {t.disputeTitle}
                        {t.disputeNumber ? ` · #${t.disputeNumber}` : ""}
                      </Link>
                    ) : (
                      <span>مهمة إدارية</span>
                    )}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2 sm:justify-end">
                  <Badge tone={prio.tone} className="text-[9px]">
                    {prio.text}
                  </Badge>
                  <Badge
                    tone={
                      urg.tone === "danger" ?
                        "danger"
                      : urg.tone === "warning" ?
                        "warning"
                      : "neutral"
                    }
                    className="text-[9px] font-mono ltr-nums"
                  >
                    {urg.label}
                  </Badge>
                  <Button variant="outline" size="sm" className="h-7 text-[0.6875rem]" asChild>
                    <Link
                      href={
                        t.caseId
                          ? `/cases/${t.caseId}?section=tasks`
                          : t.disputeId
                            ? `/disputes/${t.disputeId}`
                            : "/tasks"
                      }
                    >
                      فتح
                    </Link>
                  </Button>
                </div>
              </li>
            );
          })}
        </ul>
      }
    </SectionCard>
  );
}
