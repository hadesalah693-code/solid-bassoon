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
import { notifySuccess } from "@/lib/feedback";

export type CompleteDisputeActionDialogProps = {
  open: boolean;
  disputeId: string;
  actionId: string | null;
  actionTitle?: string;
  onClose: () => void;
  onCompleted: () => void;
};

export function CompleteDisputeActionDialog({
  open,
  disputeId,
  actionId,
  actionTitle,
  onClose,
  onCompleted,
}: CompleteDisputeActionDialogProps) {
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setNote("");
    setError(null);
  }, [open, actionId]);

  async function submit() {
    const trimmed = note.trim();
    if (!trimmed || !actionId) {
      setError("يرجى توضيح ما تم في هذا الإجراء.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await disputesApi.completeStageAction(disputeId, actionId, {
        completionNote: trimmed,
      });
      notifySuccess("تم إكمال الإجراء");
      onCompleted();
      onClose();
    } catch (e) {
      setError(
        e instanceof ApiRequestError ? e.details?.message ?? e.message : "تعذر إتمام الإجراء",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AnimatePresence>
      {open && actionId ? (
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
                <h2 className="text-base font-semibold text-[var(--color-foreground)]">إتمام الإجراء</h2>
                {actionTitle ? (
                  <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">{actionTitle}</p>
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
                  <Label htmlFor="complete-note">ماذا تم في هذا الإجراء؟</Label>
                  <Textarea
                    id="complete-note"
                    rows={5}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    disabled={submitting}
                    placeholder="مثال: تم الاتصال بالخصم وتسجيل رده..."
                  />
                </div>
              </div>
            </div>

            <div className="flex shrink-0 justify-end gap-2 border-t border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 px-5 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
              <Button type="button" variant="outline" disabled={submitting} onClick={onClose}>
                إلغاء
              </Button>
              <Button type="button" variant="gold" disabled={submitting} onClick={() => void submit()}>
                {submitting ? "جارٍ الحفظ…" : "تأكيد الإتمام"}
              </Button>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
