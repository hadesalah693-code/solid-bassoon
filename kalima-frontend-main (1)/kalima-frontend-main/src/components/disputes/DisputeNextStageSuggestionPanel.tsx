"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import type { CloseDisputeStageSuccessMeta } from "@/components/disputes/CloseDisputeStageDialog";
import { disputesApi } from "@/lib/api/disputes";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { suggestNextStageAfterClose } from "@/lib/disputes/suggestNextStage";

type DisputeNextStageSuggestionPanelProps = {
  disputeId: string;
  meta: CloseDisputeStageSuccessMeta;
  onDismiss: () => void;
  onRefresh: () => void | Promise<void>;
};

export function DisputeNextStageSuggestionPanel({
  disputeId,
  meta,
  onDismiss,
  onRefresh,
}: DisputeNextStageSuggestionPanelProps) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const suggestion = suggestNextStageAfterClose(meta.stageType, meta.outcome);

  if (!suggestion) return null;

  async function createStage() {
    const sug = suggestNextStageAfterClose(meta.stageType, meta.outcome);
    if (!sug || sug.kind !== "NEW_STAGE") return;
    setBusy(true);
    try {
      const created = await disputesApi.addStage(disputeId, {
        stageType: sug.stageType,
        status: "ACTIVE",
        title: sug.titleAr,
      });
      notifySuccess("تم إنشاء المرحلة");
      await onRefresh();
      onDismiss();
      router.push(`/disputes/${disputeId}/stages/${created.id}`);
    } catch (e) {
      notifyApiError(e, "تعذر إنشاء المرحلة");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      dir="rtl"
      className="rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 p-4"
    >
      <p className="text-sm font-semibold text-[var(--color-foreground)]">
        الخطوة المقترحة التالية
      </p>
      {suggestion.kind === "NEW_STAGE" ? (
        <>
          <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">
            بناءً على نتيجة المرحلة، نقترح إنشاء مرحلة:{" "}
            <span className="font-medium text-[var(--color-foreground)]">{suggestion.titleAr}</span>
          </p>
          {suggestion.footnoteAr ? (
            <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">{suggestion.footnoteAr}</p>
          ) : null}
          <div className="mt-3 flex flex-wrap gap-2">
            <Button
              type="button"
              variant="gold"
              size="sm"
              disabled={busy}
              onClick={() => void createStage()}
            >
              {busy ? "جارٍ الإنشاء…" : "إنشاء المرحلة المقترحة"}
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={busy} onClick={onDismiss}>
              ليس الآن
            </Button>
          </div>
        </>
      ) : (
        <>
          <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">{suggestion.messageAr}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onDismiss}>
              ليس الآن
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
