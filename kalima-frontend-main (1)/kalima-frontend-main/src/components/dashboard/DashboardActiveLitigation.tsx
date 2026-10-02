"use client";

import Link from "next/link";
import { ArrowLeft, CalendarClock, ChevronLeft, User } from "lucide-react";

import { DisputePathMiniTimeline } from "@/components/dashboard/DisputePathMiniTimeline";
import { SectionCard } from "@/components/shared/SectionCard";
import { HearingStatusBadge } from "@/components/shared/StatusBadge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  compareDisputeEvents,
  disputeCurrentStage,
  DISPUTE_EVENT_TYPE_AR,
} from "@/lib/cases/dispute-events";
import { cn } from "@/lib/utils/cn";
import { formatDateTime, formatRelative } from "@/lib/utils/format-date";
import type {
  CaseResponseDto,
  DisputeEventResponseDto,
  TaskResponseDto,
} from "@/types/api";
import type { Hearing } from "@/types/hearing";

export type DashboardLitigationRow = {
  case: CaseResponseDto;
  eventsSorted: DisputeEventResponseDto[];
  openTasks: TaskResponseDto[];
  hearing: Hearing | null;
};

function partyName(p: { fullName: string | null } | null | undefined): string {
  if (!p) return "—";
  return p.fullName?.trim() || "—";
}

function nextOpenTask(tasks: TaskResponseDto[]): TaskResponseDto | null {
  const open = tasks.filter((t) => t.status !== "DONE" && t.status !== "CANCELLED");
  if (open.length === 0) return null;
  const todayKey = new Date().toISOString().slice(0, 10);
  open.sort((a, b) => {
    const ao = a.dueDate && a.dueDate.slice(0, 10) < todayKey ? 0 : 1;
    const bo = b.dueDate && b.dueDate.slice(0, 10) < todayKey ? 0 : 1;
    if (ao !== bo) return ao - bo;
    if (!a.dueDate && !b.dueDate) return 0;
    if (!a.dueDate) return 1;
    if (!b.dueDate) return -1;
    return a.dueDate < b.dueDate ? -1 : 1;
  });
  return open[0] ?? null;
}

function taskDeadlineLabel(
  task: TaskResponseDto,
): { text: string; urgent: boolean } {
  const todayKey = new Date().toISOString().slice(0, 10);
  if (!task.dueDate) return { text: "بدون موعد", urgent: false };
  const d = task.dueDate.slice(0, 10);
  if (d < todayKey)
    return { text: `متأخرة (${formatDateTime(task.dueDate)})`, urgent: true };
  if (d === todayKey) return { text: "مستحقّة اليوم", urgent: true };
  return { text: formatDateTime(task.dueDate), urgent: false };
}

export function DashboardActiveLitigation({ rows }: { rows: DashboardLitigationRow[] }) {
  return (
    <SectionCard
      className="shadow-[var(--shadow-xs)]"
      title="القضايا النشطة"
      description="ملفات مرتبطة بمسارات النزاع والجدول اليومي."
      action={
        <Button variant="ghost" size="sm" asChild>
          <Link href="/cases">إدارة القضايا</Link>
        </Button>
      }
      flush
    >
      {rows.length === 0 ?
        <div className="px-6 py-12 text-center text-sm text-[var(--color-muted-foreground)]">
          لا ملفات في نطاق العيّنة الحالية.
        </div>
      :
        <ul className="divide-y divide-[var(--color-border-subtle)]">
        {rows.map(({ case: c, eventsSorted, openTasks, hearing }) => {
          const current =
            disputeCurrentStage(eventsSorted) ??
            [...eventsSorted].sort(compareDisputeEvents)[0] ??
            null;
          const nextTask = nextOpenTask(openTasks);
          const tMeta = nextTask ? taskDeadlineLabel(nextTask) : null;
          const clientLabel = partyName(c.client);
          const lawyerLabel = partyName(c.assignedLawyer);
          const pathEvents = [...eventsSorted].sort(compareDisputeEvents);

          return (
            <li key={c.id} className="px-5 py-4">
              <div className="flex flex-col gap-3.5">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <Link
                      href={`/cases/${c.id}`}
                      className="block text-[0.9375rem] font-bold leading-snug text-[var(--color-foreground)] underline-offset-2 hover:underline"
                    >
                      {c.title}
                    </Link>
                    <div className="mt-1.5 flex flex-wrap gap-x-2 gap-y-1 text-[0.6875rem] text-[var(--color-muted-foreground)]">
                      <span className="inline-flex items-center gap-1 font-mono ltr-nums">
                        #{c.caseNumber}
                      </span>
                      <span aria-hidden className="text-[var(--color-border-strong)]">·</span>
                      <span className="inline-flex items-center gap-1">
                        <User className="size-3 shrink-0 opacity-60" aria-hidden />
                        الموكل: {clientLabel}
                      </span>
                      <span aria-hidden className="text-[var(--color-border-strong)]">·</span>
                      <span>
                        {lawyerLabel !== "—" ? `المكلف: ${lawyerLabel}` : "لا محامٍ مكلّف"}
                      </span>
                    </div>
                  </div>
                  <Button variant="gold" size="sm" asChild className="shrink-0 gap-1">
                    <Link href={`/cases/${c.id}?section=overview`}>
                      فتح الملف
                      <ChevronLeft className="size-3.5 opacity-80" aria-hidden />
                    </Link>
                  </Button>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-[0.625rem] font-bold uppercase tracking-wider text-[var(--color-muted-foreground)]">
                    المرحلة
                  </span>
                  {current ?
                    <span
                      className={cn(
                        "inline-flex max-w-full items-center gap-1.5 rounded-lg border px-2 py-0.5 text-[0.6875rem] font-bold",
                        current.status === "CURRENT" ?
                          "border-[var(--color-gold-200)] bg-[var(--color-gold-50)] text-[var(--color-gold-600)]"
                        : "border-[var(--color-border)] bg-[var(--color-surface-muted)] text-[var(--color-foreground-secondary)]",
                      )}
                    >
                      <span
                        className={cn(
                          "size-1.5 shrink-0 rounded-full",
                          current.status === "CURRENT" ?
                            "bg-[var(--color-gold-400)]"
                          : "bg-[var(--color-muted-foreground)]/50",
                        )}
                        aria-hidden
                      />
                      {DISPUTE_EVENT_TYPE_AR[current.type] ?? current.title}
                    </span>
                  :
                    <span className="text-[0.6875rem] text-[var(--color-muted-foreground)]">
                      لا مراحل مسجّلة
                    </span>
                  }
                </div>

                <DisputePathMiniTimeline events={pathEvents} />

                {(hearing || nextTask) ?
                  <div className="grid gap-3 border-t border-dashed border-[var(--color-border-subtle)] pt-3 sm:grid-cols-2">
                    {hearing ?
                      <div className="flex gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 px-3 py-2.5">
                        <CalendarClock className="mt-0.5 size-4 shrink-0 text-[var(--color-navy-500)]" />
                        <div className="min-w-0 flex-1 text-[0.75rem]">
                          <p className="font-semibold text-[var(--color-foreground)]">
                            موعد اليوم:{" "}
                            <span className="font-mono font-bold ltr-nums text-[var(--color-navy-700)]">
                              {formatDateTime(hearing.scheduledAt)}
                            </span>
                          </p>
                          <div className="mt-1 flex flex-wrap gap-2">
                            <HearingStatusBadge value={hearing.status} />
                            {hearing.court ?
                              <span className="text-[var(--color-muted-foreground)]">
                                {hearing.court}
                              </span>
                            : null}
                          </div>
                        </div>
                      </div>
                    : null}

                    {nextTask && tMeta ?
                      <div
                        className={cn(
                          "flex gap-3 rounded-xl border px-3 py-2.5",
                          tMeta.urgent ?
                            "border-[var(--color-danger-border)] bg-[var(--color-danger-bg)]"
                          : "border-[var(--color-border)] bg-[var(--color-surface-muted)]/40",
                        )}
                      >
                        <ArrowLeft
                          className={cn(
                            "mt-0.5 size-4 shrink-0",
                            tMeta.urgent ?
                              "text-[var(--color-danger)]"
                            : "text-[var(--color-muted-foreground)]",
                          )}
                          aria-hidden
                        />
                        <div className="min-w-0 flex-1 text-[0.75rem]">
                          <p className="font-semibold text-[var(--color-foreground)]">
                            مهمة تشغيلية: {nextTask.title}
                          </p>
                          <div className="mt-1 flex flex-wrap items-center gap-2">
                            <Badge
                              tone={
                                nextTask.priority === "URGENT" ?
                                  "danger"
                                : nextTask.priority === "HIGH" ?
                                  "warning"
                                : "neutral"
                              }
                              className="text-[9px]"
                            >
                              {nextTask.priority === "URGENT" ?
                                "عرض مستعجل"
                              : nextTask.priority === "HIGH" ?
                                "أولوية عالية"
                              : "أولوية عادية"}
                            </Badge>
                            <span
                              className={cn(
                                "font-medium ltr-nums",
                                tMeta.urgent ?
                                  "text-[var(--color-danger)]"
                                : "text-[var(--color-muted-foreground)]",
                              )}
                            >
                              {tMeta.text}
                            </span>
                            <Link
                              href={`/cases/${c.id}?section=tasks`}
                              className="text-[0.6875rem] font-bold text-[var(--color-navy-600)] underline-offset-2 hover:underline"
                            >
                              المهام
                            </Link>
                          </div>
                        </div>
                      </div>
                    : null}
                  </div>
                : null}

                <p className="text-[0.625rem] text-[var(--color-muted-foreground)]">
                  آخر تحديث:{" "}
                  <span className="font-mono ltr-nums">{formatRelative(c.updatedAt)}</span>
                </p>
              </div>
            </li>
          );
        })}
        </ul>
      }
    </SectionCard>
  );
}
