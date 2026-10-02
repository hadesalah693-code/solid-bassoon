"use client";

import { Check, Circle, CircleDot } from "lucide-react";

import { cn } from "@/lib/utils/cn";
import { DISPUTE_EVENT_TYPE_AR } from "@/lib/cases/dispute-events";
import type { DisputeEventResponseDto } from "@/types/api";

type DisputePathMiniTimelineProps = {
  events: DisputeEventResponseDto[];
  max?: number;
};

export function DisputePathMiniTimeline({
  events,
  max = 5,
}: DisputePathMiniTimelineProps) {
  const slice = events.slice(0, max);
  if (slice.length === 0) return null;

  return (
    <div className="w-full overflow-x-auto overscroll-x-contain [-ms-overflow-style:none] [scrollbar-width:thin]" dir="rtl">
      <ol className="inline-flex min-w-0 flex-nowrap items-center gap-0 pb-0.5">
        {slice.map((ev, i) => {
          const label = DISPUTE_EVENT_TYPE_AR[ev.type] ?? ev.type;
          const done = ev.status === "DONE";
          const cur = ev.status === "CURRENT";
          const cancelled = ev.status === "CANCELLED";
          const last = i === slice.length - 1;
          const upcoming = ev.status === "UPCOMING" && !cancelled;

          return (
            <li key={ev.id} className="flex shrink-0 items-center">
              <span className="flex max-w-[5.5rem] flex-col items-center gap-1 px-0.5 text-center">
                <span
                  className={cn(
                    "flex size-7 items-center justify-center rounded-full border-[1.5px] shadow-sm [&_svg]:size-3.5",
                    done &&
                      "border-[var(--color-success-border)] bg-[var(--color-success-bg)] text-[var(--color-success)]",
                    cur &&
                      "scale-105 border-[var(--color-gold-400)] bg-[var(--color-gold-50)] text-[var(--color-gold-600)] ring-2 ring-[var(--color-gold-400)]/15",
                    upcoming &&
                      "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-muted-foreground)]",
                    cancelled &&
                      "border-[var(--color-border)] opacity-40 saturate-75",
                  )}
                  title={label}
                  aria-hidden
                >
                  {done ? (
                    <Check />
                  ) : cur ? (
                    <CircleDot className="text-[var(--color-gold-500)]" />
                  ) : (
                    <Circle />
                  )}
                </span>
                <span className="line-clamp-2 text-[8px] font-bold leading-tight text-[var(--color-foreground-secondary)]">
                  {label}
                </span>
              </span>
              {!last ?
                <span
                  aria-hidden
                  className={cn(
                    "mx-0.5 h-px min-w-[0.75rem] max-w-[1.5rem] flex-1 rounded-full bg-[var(--color-border)]",
                    slice[i + 1]?.status === "CURRENT" &&
                      "bg-gradient-to-r from-[var(--color-border)] to-[var(--color-gold-400)]/40",
                  )}
                />
              : null}
            </li>
          );
        })}
      </ol>
    </div>
  );
}
