"use client";

import { useMemo, useState } from "react";
import { CheckCircle2, CircleSlash, ListTodo } from "lucide-react";

import { DisputeActionDialog } from "@/components/disputes/DisputeActionDialog";
import { DisputeStageActionRow } from "@/components/disputes/DisputeStageActionRow";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { disputeStageTypeAr, suggestedDisputeActionTypes } from "@/lib/disputes/labels";
import type {
  DisputeActionSummaryDto,
  DisputeStage,
  DisputeStageActionDto,
  DisputeStageActionTypeDto,
} from "@/types/dispute";

function isActiveAction(status: string) {
  const u = status.toUpperCase();
  return u === "OPEN" || u === "IN_PROGRESS";
}

export type DisputeActionsSectionProps = {
  disputeId: string;
  stages: DisputeStage[];
  actions: DisputeStageActionDto[];
  summary: DisputeActionSummaryDto | null;
  onRefresh: () => void | Promise<void>;
};

export function DisputeActionsSection({
  disputeId,
  stages,
  actions,
  summary,
  onRefresh,
}: DisputeActionsSectionProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogStageId, setDialogStageId] = useState<string | null>(null);

  const suggestedForDialog = useMemo((): DisputeStageActionTypeDto[] => {
    if (!dialogStageId) {
      return ["CALL", "DOCUMENT", "FOLLOW_UP"];
    }
    const st = stages.find((s) => s.id === dialogStageId);
    return suggestedDisputeActionTypes(st?.stageType ?? "");
  }, [dialogStageId, stages]);

  const generalActions = useMemo(
    () => actions.filter((a) => !a.stageId),
    [actions],
  );

  function stageCounts(stageId: string) {
    const list = actions.filter((a) => a.stageId === stageId);
    const open = list.filter((a) => isActiveAction(a.status)).length;
    const done = list.filter((a) => a.status.toUpperCase() === "DONE").length;
    return { open, done };
  }

  function openNew(stageId: string | null) {
    setDialogStageId(stageId);
    setDialogOpen(true);
  }

  return (
    <>
      <SectionCard
        title="إجراءات ومهام"
        description={
          summary
            ? `مفتوحة ${summary.openCount} · متأخرة ${summary.overdueCount} · مكتملة ${summary.doneCount} · ملغاة ${summary.cancelledCount}`
            : "مهام قابلة للتنفيذ لكل مرحلة أو للنزاع بشكل عام."
        }
        action={
          <Button
            type="button"
            size="sm"
            variant="gold"
            className="gap-1.5"
            onClick={() => openNew(null)}
          >
            <ListTodo className="size-4" />
            إجراء جديد
          </Button>
        }
      >
        {generalActions.length === 0 && stages.length === 0 ? (
          <p className="text-sm text-[var(--color-muted-foreground)]">
            لا توجد مراحل أو إجراءات بعد. أضف مرحلة من الأعلى أو أنشئ إجراءً عاماً لهذا النزاع.
          </p>
        ) : null}

        {generalActions.length > 0 ? (
          <div className="mb-5 space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-medium text-[var(--color-foreground)]">إجراءات عامة</p>
              <Button type="button" size="sm" variant="outline" onClick={() => openNew(null)}>
                إضافة إجراء
              </Button>
            </div>
            <ul className="space-y-2">
              {generalActions.map((a) => (
                <DisputeStageActionRow
                  key={a.id}
                  disputeId={disputeId}
                  action={a}
                  onRefresh={onRefresh}
                />
              ))}
            </ul>
          </div>
        ) : (
          <div className="mb-5 rounded-xl border border-dashed border-[var(--color-border)] px-3 py-4 text-center">
            <p className="text-xs text-[var(--color-muted-foreground)]">
              لا توجد إجراءات عامة. استخدم «إجراء جديد» أو أضف مهمة من بطاقة مرحلة.
            </p>
          </div>
        )}

        <div className="space-y-4">
          {stages.map((s) => {
            const { open, done } = stageCounts(s.id);
            const list = actions.filter((a) => a.stageId === s.id);
            return (
              <div
                key={s.id}
                className="rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm"
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-[var(--color-foreground)]">
                      {disputeStageTypeAr(s.stageType)}
                    </p>
                    {s.title ? (
                      <p className="text-xs text-[var(--color-muted-foreground)]">{s.title}</p>
                    ) : null}
                    <p className="mt-1 flex flex-wrap gap-2 text-[11px] text-[var(--color-muted-foreground)]">
                      <span className="inline-flex items-center gap-1">
                        <CircleSlash className="size-3 opacity-70" />
                        مفتوحة: {open}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <CheckCircle2 className="size-3 opacity-70" />
                        مكتملة: {done}
                      </span>
                    </p>
                  </div>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => openNew(s.id)}
                  >
                    إضافة إجراء
                  </Button>
                </div>
                {list.length === 0 ? (
                  <p className="mt-3 text-xs text-[var(--color-muted-foreground)]">
                    لا مهام لهذه المرحلة بعد.
                  </p>
                ) : (
                  <ul className="mt-3 space-y-2">
                    {list.map((a) => (
                      <DisputeStageActionRow
                        key={a.id}
                        disputeId={disputeId}
                        action={a}
                        onRefresh={onRefresh}
                      />
                    ))}
                  </ul>
                )}
              </div>
            );
          })}
        </div>
      </SectionCard>

      <DisputeActionDialog
        open={dialogOpen}
        disputeId={disputeId}
        stages={stages}
        defaultStageId={dialogStageId}
        suggestedTypes={suggestedForDialog}
        onClose={() => setDialogOpen(false)}
        onSaved={() => void onRefresh()}
      />
    </>
  );
}
