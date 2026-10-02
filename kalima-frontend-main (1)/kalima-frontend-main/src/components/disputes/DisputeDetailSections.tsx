"use client";

import Link from "next/link";
import { ChevronLeft, Plus } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { SectionCard } from "@/components/shared/SectionCard";
import { cn } from "@/lib/utils/cn";
import {
  DISPUTE_TYPE_AR,
  DISPUTE_WORKFLOW_STEP_AR,
  LEGAL_PATH_AR,
  disputeCaseLinkRoleAr,
  disputeStageOutcomeAr,
  disputeStageStatusAr,
  disputeStageTypeAr,
} from "@/lib/disputes/labels";
import { actionsForStage, computeStageActionProgress } from "@/lib/disputes/stageActionProgress";
import { formatDateTime } from "@/lib/utils/format-date";
import type {
  DisputeCaseLink,
  DisputeResponseDto,
  DisputeStage,
  DisputeStageActionDto,
} from "@/types/dispute";

function isStageClosed(s: DisputeStage) {
  if (s.closedAt) return true;
  const st = s.status?.toUpperCase() ?? "";
  return st === "CLOSED" || st === "DONE" || st === "CANCELLED";
}

function isStageOpen(s: DisputeStage) {
  return !isStageClosed(s);
}

function sortStages(stages: DisputeStage[]) {
  return [...stages].sort((a, b) => {
    const ta = new Date(a.startedAt ?? a.createdAt).getTime();
    const tb = new Date(b.startedAt ?? b.createdAt).getTime();
    return ta - tb;
  });
}

function stageCardClass(active?: boolean, closed?: boolean) {
  return cn(
    "relative min-w-[14rem] max-w-[18rem] shrink-0 rounded-2xl border bg-[var(--color-surface)] p-4 shadow-[var(--shadow-soft)] transition-all",
    active &&
      "border-[var(--color-gold-400)]/55 shadow-[0_0_0_1px_rgba(212,175,55,0.25)] ring-1 ring-[var(--color-gold-400)]/15",
    closed && !active && "border-[var(--color-border)] opacity-90",
    !active && !closed && "border-[var(--color-border)]",
  );
}

export type DisputeStageStripProps = {
  row: DisputeResponseDto;
  disputeId: string;
  /** When provided (including empty array), show per-stage action progress on cards. */
  stageActions?: DisputeStageActionDto[];
  stages: DisputeStage[];
  onAddStage: () => void;
  /** User clicked «إغلاق المرحلة» — parent should open a confirmation/note dialog. */
  onRequestCloseStage?: (stageId: string) => void;
  converted: boolean;
};

export function DisputeStageStrip({
  row,
  disputeId,
  stageActions,
  stages,
  onAddStage,
  onRequestCloseStage,
  converted,
}: DisputeStageStripProps) {
  const useApiStages = stages.length > 0;
  const orderedStages = useApiStages ? sortStages(stages) : [];
  const activeIdx = orderedStages.findIndex((s) => isStageOpen(s));
  const currentIdx = activeIdx >= 0 ? activeIdx : Math.max(0, orderedStages.length - 1);

  return (
    <SectionCard
      title="المراحل والمسارات"
      description={
        useApiStages
          ? "مراحل النزاع المسجّلة. يمكن إضافة مراحل جديدة أو إغلاق المرحلة عند الانتهاء."
          : "مسار العمل الحالي والمسار القانوني المرتبط بهذا النزاع."
      }
      action={
        <Button
          type="button"
          size="sm"
          variant="gold"
          className="gap-1.5"
          disabled={converted}
          onClick={onAddStage}
        >
          <Plus className="size-4" />
          إضافة مرحلة
        </Button>
      }
    >
      {useApiStages ?
        <div className="-mx-1 overflow-x-auto px-1 pb-1">
          <div className="flex min-w-max gap-3">
            {orderedStages.map((s, idx) => {
              const active = idx === currentIdx && isStageOpen(s);
              const closed = isStageClosed(s);
              const stageActs =
                stageActions != null ? actionsForStage(stageActions, s.id) : [];
              const p = computeStageActionProgress(stageActs);

              return (
                <div key={s.id} className={stageCardClass(active, closed)}>
                  <div className="mb-3 flex items-center justify-between gap-2">
                    <span
                      className={cn(
                        "flex size-7 items-center justify-center rounded-full text-xs font-bold",
                        active ?
                          "bg-[var(--color-gold-400)] text-[var(--color-navy-950)]"
                        : closed ?
                          "bg-[var(--color-success-bg)] text-[var(--color-success)]"
                        : "bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
                      )}
                    >
                      {idx + 1}
                    </span>
                    {active ?
                      <Badge tone="gold" className="text-[10px]">
                        نشطة
                      </Badge>
                    : closed ?
                      <Badge tone="success" className="text-[10px]">
                        مغلقة
                      </Badge>
                    : null}
                  </div>
                  <p className="text-xs text-[var(--color-muted-foreground)]">نوع المرحلة</p>
                  <p className="mt-1 text-sm font-semibold leading-snug text-[var(--color-foreground)]">
                    {disputeStageTypeAr(s.stageType)}
                  </p>
                  <p className="mt-1.5 line-clamp-2 text-xs text-[var(--color-muted-foreground)]">
                    {s.title ?? "—"}
                  </p>
                  {(s.startedAt ?? s.createdAt) ?
                    <p className="mt-2 text-[10px] text-[var(--color-muted-foreground)] ltr-nums">
                      {formatDateTime(s.startedAt ?? s.createdAt!)}
                    </p>
                  : null}
                  <div className="mt-2 flex flex-wrap items-center gap-1.5">
                    <Badge tone="neutral" className="text-[10px]">
                      {disputeStageStatusAr(s.status)}
                    </Badge>
                    {closed && s.outcome ?
                      <Badge tone="info" className="text-[10px]">
                        {disputeStageOutcomeAr(s.outcome)}
                      </Badge>
                    : null}
                  </div>
                  {stageActions != null ?
                    <div className="mt-3 space-y-1.5">
                      <div className="flex flex-wrap items-center justify-between gap-2 text-[10px] text-[var(--color-muted-foreground)]">
                        <span className="tabular-nums ltr-nums">
                          مكتمل {p.completed} / {p.totalForProgress}
                        </span>
                        {p.overdue > 0 ?
                          <span className="font-medium text-amber-700">متأخر {p.overdue}</span>
                        : null}
                      </div>
                      <div className="h-1.5 overflow-hidden rounded-full bg-[var(--color-surface-muted)]">
                        <div
                          className="h-full rounded-full bg-[var(--color-gold-400)] transition-[width] duration-300"
                          style={{
                            width: `${p.totalForProgress > 0 ? p.percentage : 0}%`,
                          }}
                        />
                      </div>
                    </div>
                  : null}
                  <Button type="button" variant="gold" size="sm" className="mt-3 w-full" asChild>
                    <Link href={`/disputes/${disputeId}/stages/${s.id}`}>
                      فتح المرحلة
                      <ChevronLeft className="size-3.5 opacity-80" aria-hidden />
                    </Link>
                  </Button>
                  {onRequestCloseStage && isStageOpen(s) && !converted ?
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="mt-2 w-full"
                      onClick={() => onRequestCloseStage(s.id)}
                    >
                      إغلاق المرحلة
                    </Button>
                  : null}
                </div>
              );
            })}
          </div>
        </div>
      : (
        <>
          <p className="mb-3 text-sm text-[var(--color-muted-foreground)]">
            لا توجد مراحل بعد. يمكنك إضافة مرحلة، أو الاعتماد على الملخص أدناه من بيانات النزاع.
          </p>
          <div className="grid gap-3 sm:grid-cols-3">
            <div className={stageCardClass(true)}>
              <p className="text-xs text-[var(--color-muted-foreground)]">المسار القانوني</p>
              <p className="mt-1 text-sm font-semibold text-[var(--color-foreground)]">
                {row.legalPath ? LEGAL_PATH_AR[row.legalPath] : "— لم يُحدد —"}
              </p>
              <Badge tone="gold" className="mt-2 text-[10px]">
                نشط
              </Badge>
            </div>
            <div className={stageCardClass(true)}>
              <p className="text-xs text-[var(--color-muted-foreground)]">مرحلة سير العمل</p>
              <p className="mt-1 text-sm font-semibold text-[var(--color-foreground)]">
                {DISPUTE_WORKFLOW_STEP_AR[row.currentStep]}
              </p>
              <Badge tone="info" className="mt-2 text-[10px]">
                الحالية
              </Badge>
            </div>
            <div className={stageCardClass(false)}>
              <p className="text-xs text-[var(--color-muted-foreground)]">تصنيف النزاع</p>
              <p className="mt-1 text-sm font-semibold text-[var(--color-foreground)]">
                {DISPUTE_TYPE_AR[row.disputeType]}
              </p>
              <Badge tone="neutral" className="mt-2 text-[10px]">
                معلومات
              </Badge>
            </div>
          </div>
        </>
      )}
    </SectionCard>
  );
}

export type DisputeLinkedCasesProps = {
  links: DisputeCaseLink[];
  legacyConvertedCaseId: string | null;
  onRequestLink?: () => void;
  onUnlinkCase?: (caseId: string) => void | Promise<void>;
  unlinkingCaseId?: string | null;
};

export function DisputeLinkedCases({
  links,
  legacyConvertedCaseId,
  onRequestLink,
  onUnlinkCase,
  unlinkingCaseId = null,
}: DisputeLinkedCasesProps) {
  const showLinks = links.length > 0;
  const showLegacy = !showLinks && !!legacyConvertedCaseId;

  return (
    <SectionCard
      title="القضايا المرتبطة"
      action={
        onRequestLink ? (
          <Button type="button" size="sm" variant="outline" onClick={onRequestLink}>
            ربط قضية
          </Button>
        ) : null
      }
    >
      {showLinks ? (
        <ul className="space-y-3">
          {links.map((link) => (
            <li
              key={link.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 px-4 py-3"
            >
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">قضية مرتبطة</p>
                <p className="mt-0.5 font-mono text-xs text-[var(--color-muted-foreground)]">
                  {link.caseId.slice(0, 8)}…
                </p>
                {link.linkRole ? (
                  <p className="mt-1 text-[11px] text-[var(--color-muted-foreground)]">
                    الدور: {disputeCaseLinkRoleAr(link.linkRole)}
                  </p>
                ) : null}
              </div>
              <div className="flex shrink-0 flex-wrap items-center gap-2">
                {onUnlinkCase ? (
                  <Button
                    type="button"
                    variant="destructive"
                    size="sm"
                    disabled={unlinkingCaseId === link.caseId}
                    onClick={() => {
                      if (
                        typeof window !== "undefined" &&
                        window.confirm("هل تؤكد إلغاء ربط هذه القضية عن النزاع؟")
                      ) {
                        void onUnlinkCase(link.caseId);
                      }
                    }}
                  >
                    {unlinkingCaseId === link.caseId ? "جارٍ الإلغاء…" : "إلغاء الربط"}
                  </Button>
                ) : null}
                <Button variant="gold" size="sm" asChild>
                  <Link href={`/cases/${link.caseId}`}>فتح القضية</Link>
                </Button>
              </div>
            </li>
          ))}
        </ul>
      ) : showLegacy ? (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 px-4 py-3">
            <div>
              <p className="text-sm font-medium">قضية من التحويل السابق</p>
              <p className="mt-0.5 font-mono text-xs text-[var(--color-muted-foreground)]">
                {legacyConvertedCaseId!.slice(0, 8)}…
              </p>
            </div>
            <Button variant="gold" size="sm" asChild>
              <Link href={`/cases/${legacyConvertedCaseId}`}>فتح القضية</Link>
            </Button>
          </div>
          <p className="text-xs text-[var(--color-muted-foreground)]">
            لا توجد صفوف ربط في النظام الجديد بعد — يُعرض معرّف القضية المحفوظ في النزاع.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-[var(--color-muted-foreground)]">
            لا توجد قضايا مرتبطة بعد. اضغط «ربط قضية» لاختيار ملف موجود، أو استخدم «تحويل إلى قضية»
            لإنشاء قضية جديدة وربطها.
          </p>
        </div>
      )}
    </SectionCard>
  );
}
