"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useEffect, useState } from "react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { disputesApi } from "@/lib/api/disputes";
import { ApiRequestError } from "@/lib/api/client";
import { DISPUTE_STAGE_OUTCOME_OPTIONS, type DisputeStageOutcomeCodeDto } from "@/lib/disputes/labels";
import { notifySuccess } from "@/lib/feedback";

export type CloseDisputeStageSuccessMeta = {
  outcome: string;
  stageType: string;
};

function arCloseStageError(message: string): string {
  const m = message.trim();
  if (m.includes("outcome is required")) return "نتيجة المرحلة مطلوبة.";
  if (m.includes("closingNote")) return "ملخص ما تم في هذه المرحلة مطلوب.";
  if (m.includes("invalid outcome")) return "نتيجة المرحلة غير صالحة.";
  return m;
}

export type CloseDisputeStageDialogProps = {
  open: boolean;
  disputeId: string;
  stageId: string | null;
  /** Stage type for post-close suggestions (e.g. LEGAL_NOTICE). */
  stageType: string | null | undefined;
  stageLabel?: string;
  onClose: () => void;
  /** Called after a successful close; includes outcome and stage type for next-step UI. */
  onClosed: (meta?: CloseDisputeStageSuccessMeta) => void;
};

export function CloseDisputeStageDialog({
  open,
  disputeId,
  stageId,
  stageType,
  stageLabel,
  onClose,
  onClosed,
}: CloseDisputeStageDialogProps) {
  const [outcome, setOutcome] = useState<DisputeStageOutcomeCodeDto | "">("");
  const [note, setNote] = useState("");
  const [extraNote, setExtraNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setOutcome("");
    setNote("");
    setExtraNote("");
    setError(null);
  }, [open, stageId]);

  const canSubmit = !!stageId && !!outcome && note.trim().length > 0 && !submitting;

  async function submit() {
    if (!stageId || !stageType?.trim()) {
      setError("بيانات المرحلة غير مكتملة.");
      return;
    }
    const trimmed = note.trim();
    if (!outcome || !trimmed) {
      setError("يرجى اختيار نتيجة المرحلة وإدخال الملخص.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await disputesApi.updateStage(disputeId, stageId, {
        status: "CLOSED",
        closedAt: new Date().toISOString(),
        closingNote: trimmed,
        outcome,
        outcomeNote: extraNote.trim() || null,
      });
      notifySuccess("تم إغلاق المرحلة");
      onClosed({ outcome, stageType: stageType.trim() });
      onClose();
    } catch (e) {
      const raw =
        e instanceof ApiRequestError ? e.details?.message ?? e.message : "تعذر إغلاق المرحلة";
      setError(arCloseStageError(raw));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AnimatePresence>
      {open && stageId ? (
        <motion.div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <button
            type="button"
            className="absolute inset-0 bg-black/45 backdrop-blur-[2px]"
            aria-label="إغلاق"
            onClick={() => !submitting && onClose()}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            dir="rtl"
            initial={{ opacity: 0, y: 12, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.98 }}
            className="relative z-10 flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-xl"
          >
            <div className="flex shrink-0 items-start justify-between gap-3 border-b border-[var(--color-border)] px-5 py-4">
              <div>
                <h2 className="text-base font-semibold text-[var(--color-foreground)]">إغلاق المرحلة</h2>
                {stageLabel ? (
                  <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">{stageLabel}</p>
                ) : null}
              </div>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="shrink-0 rounded-full"
                disabled={submitting}
                onClick={onClose}
              >
                <X className="size-4" />
              </Button>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-4">
              <div className="space-y-3">
                {error ? <ApiErrorAlert message={error} /> : null}
                <div className="space-y-2">
                  <Label htmlFor="close-stage-outcome">نتيجة المرحلة</Label>
                  <select
                    id="close-stage-outcome"
                    className="flex h-10 w-full rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-foreground)] shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] disabled:cursor-not-allowed disabled:opacity-50"
                    value={outcome}
                    onChange={(e) =>
                      setOutcome(e.target.value as DisputeStageOutcomeCodeDto | "")
                    }
                    disabled={submitting}
                  >
                    <option value="">اختر النتيجة</option>
                    {DISPUTE_STAGE_OUTCOME_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="close-stage-note">ملخص ما تم في هذه المرحلة</Label>
                  <Textarea
                    id="close-stage-note"
                    rows={5}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    disabled={submitting}
                    placeholder="تم التواصل مع الخصم وتوثيق نتيجة التفاوض..."
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="close-stage-extra">ملاحظات إضافية (اختياري)</Label>
                  <Textarea
                    id="close-stage-extra"
                    rows={2}
                    value={extraNote}
                    onChange={(e) => setExtraNote(e.target.value)}
                    disabled={submitting}
                    placeholder="تفاصيل إضافية للمرجع الداخلي..."
                  />
                </div>
              </div>
            </div>

            <div className="flex shrink-0 justify-end gap-2 border-t border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 px-5 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
              <Button type="button" variant="outline" disabled={submitting} onClick={onClose}>
                إلغاء
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={!canSubmit}
                onClick={() => void submit()}
              >
                {submitting ? "جارٍ الإغلاق…" : "تأكيد إغلاق المرحلة"}
              </Button>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
