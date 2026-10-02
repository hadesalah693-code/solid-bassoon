"use client";

import {
  AlertTriangle,
  ArrowUpCircle,
  CalendarDays,
  CheckCircle2,
  CircleDot,
  FileStack,
  FileText,
  Landmark,
  Scale,
} from "lucide-react";

import { DisputeEventStatusBadge } from "@/components/shared/StatusBadge";
import { cn } from "@/lib/utils/cn";
import { formatDate, formatDateTime } from "@/lib/utils/format-date";
import type {
  DisputeEventResponseDto,
  DisputeEventStatusDto,
  DisputeEventTypeDto,
} from "@/types/api";

export type DisputeTimelineLabels = {
  typeAr: Record<DisputeEventTypeDto, string>;
};

export type DisputeEventsTimelineProps = {
  events: DisputeEventResponseDto[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  labels: DisputeTimelineLabels;
};

function typeIcon(type: DisputeEventTypeDto) {
  const common = "size-4 shrink-0";
  switch (type) {
    case "CASE_OPENED":
      return <FileText className={common} />;
    case "WARNING_NOTICE":
      return <AlertTriangle className={common} />;
    case "CLAIM_FILED":
      return <Scale className={common} />;
    case "FIRST_HEARING":
      return <CalendarDays className={common} />;
    case "MEMO_EXCHANGE":
      return <FileStack className={common} />;
    case "JUDGMENT":
      return <Landmark className={common} />;
    case "APPEAL":
      return <ArrowUpCircle className={common} />;
    default:
      return <CircleDot className={common} />;
  }
}

function statusNodeRing(
  status: DisputeEventStatusDto,
  selected: boolean,
) {
  return cn(
    "flex size-11 shrink-0 items-center justify-center rounded-full border-2 bg-[var(--color-surface)] shadow-sm transition-all",
    status === "CURRENT" &&
      "border-[var(--color-gold-400)] bg-[var(--color-gold-50)] shadow-[var(--shadow-pop)] ring-2 ring-[var(--color-gold-400)]/25",
    status === "DONE" &&
      "border-[var(--color-success)]/60 bg-[var(--color-success-bg)]/90 text-[var(--color-success)]",
    status === "UPCOMING" &&
      "border-[var(--color-border)] text-[var(--color-muted-foreground)] opacity-95",
    status === "CANCELLED" &&
      "border-[var(--color-border)] opacity-55 saturate-50",
    selected &&
      status !== "CURRENT" &&
      "ring-2 ring-[var(--color-navy-700)]/20 ring-offset-2 ring-offset-[var(--color-background)]",
  );
}

function eventWhenLabel(ev: DisputeEventResponseDto): string | null {
  if (!ev.eventDate) return null;
  return ev.eventDate.includes("T")
    ? formatDateTime(ev.eventDate)
    : formatDate(ev.eventDate);
}

function TimelineNodeDesktop(props: {
  ev: DisputeEventResponseDto;
  idx: number;
  total: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
  labels: DisputeTimelineLabels;
}) {
  const { ev, idx, total, selectedId, onSelect, labels } = props;
  const sel = selectedId === ev.id;
  const nodeInner =
    ev.status === "DONE" ? (
      <CheckCircle2 className="size-5 text-[var(--color-success)]" />
    ) : (
      typeIcon(ev.type)
    );
  const whenLabel = eventWhenLabel(ev);

  return (
    <li className="flex shrink-0 items-start">
      <div className="flex min-w-[6.75rem] max-w-[10rem] flex-col items-center gap-2">
        <button
          type="button"
          aria-pressed={sel}
          aria-current={ev.status === "CURRENT" ? "step" : undefined}
          aria-label={`${labels.typeAr[ev.type]}: ${ev.title}`}
          onClick={() => onSelect(ev.id)}
          className={cn(
            "flex flex-col items-center gap-1.5 text-center outline-none",
            "focus-visible:rounded-xl focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
          )}
        >
          <span className={statusNodeRing(ev.status, sel)}>{nodeInner}</span>
          <span className="line-clamp-2 px-0.5 text-[11px] font-semibold leading-tight text-[var(--color-foreground)] md:text-xs">
            {ev.title}
          </span>
          {whenLabel ? (
            <span className="font-mono text-[10px] text-[var(--color-muted-foreground)] ltr-nums">
              {whenLabel}
            </span>
          ) : null}
          <DisputeEventStatusBadge value={ev.status} />
          {ev.status === "CURRENT" ? (
            <span className="rounded-full bg-[var(--color-gold-400)]/20 px-2 py-0.5 text-[9px] font-bold text-[var(--color-gold-700)]">
              المرحلة الحالية
            </span>
          ) : null}
        </button>
      </div>
      {idx < total - 1 ? (
        <div
          aria-hidden
          className="mx-1 mt-5 h-0.5 min-w-[1.5rem] max-w-[3rem] flex-1 rounded-full bg-[var(--color-border)]"
        />
      ) : null}
    </li>
  );
}

function TimelineVerticalMobile(props: DisputeEventsTimelineProps) {
  const { events, selectedId, onSelect, labels } = props;
  const n = events.length;

  return events.map((ev, index) => {
    const sel = selectedId === ev.id;
    const nodeInner =
      ev.status === "DONE" ? (
        <CheckCircle2 className="size-5 text-[var(--color-success)]" />
      ) : (
        typeIcon(ev.type)
      );
    const whenLabel = eventWhenLabel(ev);
    const notLast = index < n - 1;

    return (
      <li key={ev.id} className="relative flex gap-3 pe-1">
        {notLast ? (
          <span
            aria-hidden
            className="pointer-events-none absolute end-[1.25rem] top-12 bottom-[-0.5rem] w-0.5 bg-[var(--color-border)]"
          />
        ) : null}
        <div className="relative z-[1] flex shrink-0 flex-col items-center">
          <button
            type="button"
            aria-pressed={sel}
            onClick={() => onSelect(ev.id)}
            className="rounded-full outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]"
          >
            <span className={statusNodeRing(ev.status, sel)}>{nodeInner}</span>
          </button>
        </div>
        <button
          type="button"
          onClick={() => onSelect(ev.id)}
          className={cn(
            "min-w-0 flex-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 p-3 text-start shadow-sm outline-none transition-colors",
            sel &&
              "border-[var(--color-gold-400)]/70 bg-[var(--color-navy-50)]/60 shadow-[var(--shadow-soft)]",
            "focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
          )}
        >
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <DisputeEventStatusBadge value={ev.status} />
            <span className="text-[11px] text-[var(--color-muted-foreground)]">
              {labels.typeAr[ev.type]}
            </span>
            {ev.status === "CURRENT" ? (
              <span className="rounded-full bg-[var(--color-gold-400)]/20 px-2 py-0.5 text-[9px] font-bold text-[var(--color-gold-700)]">
                المرحلة الحالية
              </span>
            ) : null}
          </div>
          <p className="font-semibold leading-snug text-[var(--color-foreground)]">
            {ev.title}
          </p>
          {whenLabel ? (
            <p className="mt-1 font-mono text-xs text-[var(--color-muted-foreground)] ltr-nums">
              {whenLabel}
            </p>
          ) : null}
        </button>
      </li>
    );
  });
}

/** RTL-aware timeline: horizontal scroll on md+, vertical on small screens. */
export function DisputeEventsTimeline(props: DisputeEventsTimelineProps) {
  const { events } = props;
  if (events.length === 0) return null;
  const n = events.length;

  return (
    <div className="rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]/80 px-3 py-4 shadow-[var(--shadow-soft)] sm:px-4">
      <h3 className="mb-4 text-center text-xs font-semibold uppercase tracking-[0.12em] text-[var(--color-muted-foreground)] md:text-start">
        مسار مراحل النزاع
      </h3>

      <div className="relative md:hidden" dir="rtl">
        <ol className="space-y-4">
          <TimelineVerticalMobile {...props} />
        </ol>
      </div>

      <div dir="rtl" className="hidden pb-1 md:block">
        <div className="-mx-1 overflow-x-auto overscroll-x-contain">
          <ol className="inline-flex flex-nowrap items-start px-2 py-2">
            {events.map((ev, idx) => (
              <TimelineNodeDesktop
                key={ev.id}
                ev={ev}
                idx={idx}
                total={n}
                selectedId={props.selectedId}
                onSelect={props.onSelect}
                labels={props.labels}
              />
            ))}
          </ol>
        </div>
        <p className="px-2 text-[10px] leading-relaxed text-[var(--color-muted-foreground)]">
          مرّر أفقيًا لعرض جميع المراحل. انقر مرحلة لعرض التفاصيل أسفل المخطط.
        </p>
      </div>
    </div>
  );
}
