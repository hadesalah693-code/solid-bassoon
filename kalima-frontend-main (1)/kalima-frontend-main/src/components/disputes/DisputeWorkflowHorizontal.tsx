"use client";

import type { LucideIcon } from "lucide-react";
import {
  Bell,
  Building2,
  Check,
  CheckCircle2,
  Gavel,
  Handshake,
  Landmark,
  MessageCircle,
  Scale,
} from "lucide-react";

import { cn } from "@/lib/utils/cn";
import { DISPUTE_WORKFLOW_ORDER, disputeStageTypeAr } from "@/lib/disputes/labels";
import type { DisputeStage, DisputeWorkflowStepDto } from "@/types/dispute";
import { formatDateTime } from "@/lib/utils/format-date";

const H_STEPS: {
  step: DisputeWorkflowStepDto;
  label: string;
  Icon: LucideIcon;
}[] = [
  { step: "NEGOTIATION", label: "تفاوض", Icon: MessageCircle },
  { step: "LEGAL_NOTICE", label: "إنذار", Icon: Bell },
  { step: "MEDIATION", label: "تسوية ودية", Icon: Handshake },
  { step: "COURT_FILING", label: "رفع دعوى", Icon: Building2 },
  { step: "HEARINGS", label: "جلسات", Icon: Gavel },
  { step: "JUDGMENT", label: "تنفيذ", Icon: CheckCircle2 },
];

function orderOf(s: DisputeWorkflowStepDto) {
  return DISPUTE_WORKFLOW_ORDER.indexOf(s);
}

function activeHorizontalIndex(current: DisputeWorkflowStepDto) {
  const cur = orderOf(current);
  if (current === "CLOSED") return H_STEPS.length - 1;
  let last = 0;
  H_STEPS.forEach((h, i) => {
    if (orderOf(h.step) <= cur) last = i;
  });
  return last;
}

function isStageClosed(s: DisputeStage) {
  if (s.closedAt) return true;
  const st = s.status?.toUpperCase() ?? "";
  return st === "CLOSED" || st === "DONE" || st === "CANCELLED";
}

function stageTypeIcon(stageType: string): LucideIcon {
  const t = stageType.toUpperCase();
  if (t.includes("NEGOTIATION")) return MessageCircle;
  if (t.includes("NOTICE") || t.includes("WARNING")) return Bell;
  if (t.includes("MEDIATION") || t.includes("FRIENDLY") || t.includes("SETTLEMENT")) {
    return Handshake;
  }
  if (t.includes("COURT") || t.includes("FILING") || t.includes("CLAIM")) return Building2;
  if (t.includes("HEARING") || t.includes("SESSION")) return Gavel;
  if (t.includes("JUDGMENT") || t.includes("EXECUTION")) return CheckCircle2;
  if (t.includes("CRIMINAL")) return Scale;
  if (t.includes("ARBITRATION")) return Landmark;
  if (t.includes("ADMIN")) return Building2;
  return MessageCircle;
}

function sortStages(stages: DisputeStage[]) {
  return [...stages].sort((a, b) => {
    const ta = new Date(a.startedAt ?? a.createdAt).getTime();
    const tb = new Date(b.startedAt ?? b.createdAt).getTime();
    return ta - tb;
  });
}

function activeStageIndex(ordered: DisputeStage[]) {
  for (let i = ordered.length - 1; i >= 0; i--) {
    if (!isStageClosed(ordered[i])) return i;
  }
  return Math.max(0, ordered.length - 1);
}

function stageLabel(s: DisputeStage) {
  return s.title?.trim() || disputeStageTypeAr(s.stageType);
}

function stageDate(s: DisputeStage) {
  return s.startedAt ?? s.createdAt;
}

export type DisputeWorkflowHorizontalProps = {
  currentStep: DisputeWorkflowStepDto;
  /** مراحل مسجّلة — تُعرض بالتسلسل دون مراحل مستقبلية. */
  stages?: DisputeStage[];
  /** When true, all nodes look “preview” — used on create page before save. */
  draftMode?: boolean;
  /** Optional date shown under the active node (e.g. dispute updatedAt). */
  activeDate?: string | null;
  className?: string;
};

function SequentialTimeline({
  orderedStages,
  className,
}: {
  orderedStages: DisputeStage[];
  className?: string;
}) {
  const activeIdx = activeStageIndex(orderedStages);

  return (
    <div
      dir="rtl"
      className={cn(
        "rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm",
        className,
      )}
    >
      <div className="mb-1">
        <h3 className="text-sm font-semibold text-[var(--color-foreground)]">
          خط سير النزاع
        </h3>
        <p className="mt-0.5 text-xs text-[var(--color-muted-foreground)]">
          {orderedStages.length} مرحلة مسجّلة — الأحدث في النهاية
        </p>
      </div>
      <div className="overflow-x-auto pb-1 pt-2">
        <ol className="flex min-w-max items-start gap-0 px-1">
          {orderedStages.map((stage, i) => {
            const done = i < activeIdx || (isStageClosed(stage) && i !== activeIdx);
            const active = i === activeIdx;
            const Icon = stageTypeIcon(stage.stageType);
            const label = stageLabel(stage);
            const date = stageDate(stage);

            return (
              <li key={stage.id} className="flex items-start">
                <div className="flex w-[4.5rem] shrink-0 flex-col items-center sm:w-[5.5rem]">
                  <div
                    className={cn(
                      "flex size-11 items-center justify-center rounded-2xl border-2 transition-colors",
                      done &&
                        "border-emerald-600/60 bg-emerald-600/15 text-emerald-800",
                      active &&
                        "border-[var(--color-gold-400)] bg-[var(--color-gold-400)]/15 text-[var(--color-foreground)] shadow-[0_0_0_3px_rgba(212,175,55,0.22)]",
                      !done &&
                        !active &&
                        "border-[var(--color-border)] bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
                    )}
                  >
                    {done ?
                      <Check className="size-5" strokeWidth={2.25} />
                    : <Icon className="size-5" strokeWidth={1.75} />}
                  </div>
                  <p
                    className={cn(
                      "mt-2 max-w-[5.5rem] text-center text-[11px] font-medium leading-tight sm:text-xs",
                      active && "font-semibold text-[var(--color-foreground)]",
                      done && "text-[var(--color-foreground)]/90",
                      !done && !active && "text-[var(--color-muted-foreground)]",
                    )}
                  >
                    {label}
                  </p>
                  {date ?
                    <p className="mt-0.5 max-w-[5.5rem] text-center text-[10px] text-[var(--color-muted-foreground)] ltr-nums">
                      {formatDateTime(date)}
                    </p>
                  : null}
                </div>
                {i < orderedStages.length - 1 ?
                  <div
                    className={cn(
                      "mx-0.5 mt-5 h-px w-6 border-t-2 border-dashed sm:mx-1 sm:w-10",
                      i < activeIdx ? "border-emerald-600/40" : "border-[var(--color-border)]",
                    )}
                    aria-hidden
                  />
                : null}
              </li>
            );
          })}
        </ol>
      </div>
    </div>
  );
}

export function DisputeWorkflowHorizontal({
  currentStep,
  stages,
  draftMode = false,
  activeDate,
  className,
}: DisputeWorkflowHorizontalProps) {
  const orderedStages = stages && stages.length > 0 ? sortStages(stages) : [];

  if (!draftMode && orderedStages.length > 0) {
    return (
      <SequentialTimeline orderedStages={orderedStages} className={className} />
    );
  }

  const activeIdx = draftMode ? 0 : activeHorizontalIndex(currentStep);
  const activeItem = H_STEPS[activeIdx];

  if (!draftMode && activeItem) {
    const { Icon, label, step } = activeItem;
    return (
      <div
        dir="rtl"
        className={cn(
          "rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-sm",
          className,
        )}
      >
        <h3 className="text-sm font-semibold text-[var(--color-foreground)]">
          خط سير النزاع
        </h3>
        <p className="mt-0.5 text-xs text-[var(--color-muted-foreground)]">
          المرحلة الحالية
        </p>
        <div className="mt-4 flex flex-col items-center text-center">
          <div
            className={cn(
              "flex size-16 items-center justify-center rounded-2xl border-2",
              "border-[var(--color-gold-400)] bg-[var(--color-gold-400)]/15 text-[var(--color-foreground)]",
              "shadow-[0_0_0_4px_rgba(212,175,55,0.18)]",
            )}
          >
            <Icon className="size-8" strokeWidth={1.75} />
          </div>
          <p className="mt-3 text-base font-semibold text-[var(--color-foreground)]">
            {label}
          </p>
          {activeDate ?
            <p className="mt-1 text-xs text-[var(--color-muted-foreground)] ltr-nums">
              {formatDateTime(activeDate)}
            </p>
          : null}
          <p className="sr-only">{step}</p>
        </div>
      </div>
    );
  }

  return (
    <div
      dir="rtl"
      className={cn(
        "rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm",
        className,
      )}
    >
      <div className="mb-1 flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-[var(--color-foreground)]">
          خط سير النزاع
        </h3>
        {draftMode ?
          <p className="text-xs text-[var(--color-muted-foreground)]">
            معاينة — يُضبط المسار بالكامل بعد حفظ النزاع
          </p>
        : null}
      </div>
      <div className="overflow-x-auto pb-1 pt-2">
        <div className="flex min-w-max items-start justify-between gap-0 px-1">
          {H_STEPS.map((item, i) => {
            const Icon = item.Icon;
            const done = !draftMode && i < activeIdx;
            const active = draftMode ? i === 0 : i === activeIdx;
            return (
              <div key={item.step} className="flex items-start">
                <div className="flex w-[4.5rem] shrink-0 flex-col items-center sm:w-[5.5rem]">
                  <div
                    className={cn(
                      "flex size-11 items-center justify-center rounded-2xl border-2 transition-colors",
                      done &&
                        "border-emerald-600/60 bg-emerald-600/15 text-emerald-800 dark:border-emerald-400/50 dark:text-emerald-200",
                      active &&
                        !draftMode &&
                        "border-[var(--color-gold-400)] bg-[var(--color-gold-400)]/15 text-[var(--color-foreground)] shadow-[0_0_0_3px_rgba(212,175,55,0.22)]",
                      active &&
                        draftMode &&
                        "border-[var(--color-border)] bg-[var(--color-muted)]/30 text-[var(--color-muted-foreground)]",
                      !done &&
                        !active &&
                        "border-[var(--color-border)] bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
                    )}
                  >
                    <Icon className="size-5" strokeWidth={1.75} />
                  </div>
                  <p
                    className={cn(
                      "mt-2 max-w-[5.5rem] text-center text-[11px] font-medium leading-tight sm:text-xs",
                      active && "text-[var(--color-foreground)]",
                      !active && "text-[var(--color-muted-foreground)]",
                    )}
                  >
                    {item.label}
                  </p>
                  {active && !draftMode && activeDate ?
                    <p className="mt-0.5 max-w-[5.5rem] text-center text-[10px] text-[var(--color-muted-foreground)]">
                      {formatDateTime(activeDate)}
                    </p>
                  : null}
                  {draftMode && i === 0 ?
                    <span className="mt-1 rounded-full bg-[var(--color-gold-400)]/20 px-2 py-0.5 text-[10px] font-medium text-[var(--color-foreground)]">
                      معاينة
                    </span>
                  : null}
                </div>
                {i < H_STEPS.length - 1 ?
                  <div
                    className={cn(
                      "mx-0.5 mt-5 h-px w-6 border-t-2 border-dashed sm:mx-1 sm:w-10",
                      !draftMode && i < activeIdx ?
                        "border-emerald-600/40"
                      : "border-[var(--color-border)]",
                    )}
                    aria-hidden
                  />
                : null}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
