"use client";

import { useState } from "react";

import { CompleteDisputeActionDialog } from "@/components/disputes/CompleteDisputeActionDialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  disputeActionPriorityAr,
  disputeActionStatusAr,
  disputeActionTypeAr,
} from "@/lib/disputes/labels";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import { formatDateTime } from "@/lib/utils/format-date";
import { disputesApi } from "@/lib/api/disputes";
import type { DisputeStageActionDto } from "@/types/dispute";

function isActiveAction(status: string) {
  const u = status.toUpperCase();
  return u === "OPEN" || u === "IN_PROGRESS";
}

function priorityTone(priority: string): "neutral" | "info" | "warning" | "danger" {
  switch (priority.toUpperCase()) {
    case "LOW":
      return "neutral";
    case "MEDIUM":
      return "info";
    case "HIGH":
      return "warning";
    case "URGENT":
      return "danger";
    default:
      return "neutral";
  }
}

export type DisputeStageActionRowProps = {
  disputeId: string;
  action: DisputeStageActionDto;
  onRefresh: () => void | Promise<void>;
};

export function DisputeStageActionRow({ disputeId, action: a, onRefresh }: DisputeStageActionRowProps) {
  const [busyId, setBusyId] = useState<string | null>(null);
  const [completeOpen, setCompleteOpen] = useState(false);

  const canAct = isActiveAction(a.status);
  const done = a.status.toUpperCase() === "DONE";

  async function cancelOne() {
    if (!window.confirm("إلغاء هذا الإجراء؟ يمكنك الإبقاء على السجل كـ «ملغي».")) {
      return;
    }
    setBusyId(a.id);
    try {
      await disputesApi.cancelStageAction(disputeId, a.id);
      notifySuccess("تم إلغاء الإجراء");
      await onRefresh();
    } catch (e) {
      notifyApiError(e, "تعذر إلغاء الإجراء");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <li
        className={cn(
          "rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/25 px-3 py-2.5 text-sm",
          a.overdue && canAct
            ? "border-amber-500/40 bg-amber-500/[0.06] shadow-[inset_0_0_0_1px_rgba(245,158,11,0.12)]"
            : "",
        )}
      >
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0 flex-1">
            <p className="font-medium leading-snug text-[var(--color-foreground)]">{a.title}</p>
            <div className="mt-1 flex flex-wrap gap-1">
              <Badge tone="neutral" className="text-[10px]">
                {disputeActionTypeAr(a.actionType)}
              </Badge>
              <Badge tone={priorityTone(a.priority)} className="text-[10px]">
                {disputeActionPriorityAr(a.priority)}
              </Badge>
              <Badge tone="neutral" className="text-[10px]">
                {disputeActionStatusAr(a.status)}
              </Badge>
              {a.overdue && canAct ? (
                <Badge tone="warning" className="text-[10px]">
                  متأخر
                </Badge>
              ) : null}
            </div>
            {a.description ? (
              <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">{a.description}</p>
            ) : null}
            {done && a.completionNote?.trim() ? (
              <p className="mt-2 rounded-lg bg-[var(--color-surface-muted)]/50 px-2 py-1.5 text-xs leading-relaxed text-[var(--color-foreground)]">
                <span className="font-medium text-[var(--color-muted-foreground)]">ما تم: </span>
                {a.completionNote.trim()}
              </p>
            ) : null}
            <p className="mt-1 text-[11px] text-[var(--color-muted-foreground)]">
              {a.dueAt ? <>الاستحقاق: {formatDateTime(a.dueAt)} · </> : null}
              {done && a.completedAt ? (
                <>اكتمال: {formatDateTime(a.completedAt)} · </>
              ) : null}
              {a.completedBy?.fullName ? <>أتمَّه: {a.completedBy.fullName} · </> : null}
              {a.assignedTo?.fullName ? (
                <>مسؤول: {a.assignedTo.fullName}</>
              ) : (
                <>بدون تعيين</>
              )}
            </p>
          </div>
          {canAct ? (
            <div className="flex shrink-0 flex-col gap-1 sm:flex-row">
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="h-8 text-xs"
                disabled={busyId === a.id}
                onClick={() => setCompleteOpen(true)}
              >
                إتمام
              </Button>
              <Button
                type="button"
                size="sm"
                variant="destructive"
                className="h-8 text-xs"
                disabled={busyId === a.id}
                onClick={() => void cancelOne()}
              >
                إلغاء
              </Button>
            </div>
          ) : null}
        </div>
      </li>

      <CompleteDisputeActionDialog
        open={completeOpen}
        disputeId={disputeId}
        actionId={a.id}
        actionTitle={a.title}
        onClose={() => setCompleteOpen(false)}
        onCompleted={() => void onRefresh()}
      />
    </>
  );
}
