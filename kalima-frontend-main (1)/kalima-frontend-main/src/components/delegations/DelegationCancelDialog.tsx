"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { DELEGATION_CANCEL_REASON_AR } from "@/lib/delegations/labels";
import { cn } from "@/lib/utils/cn";
import type { DelegationCancelReasonDto } from "@/types/delegation";

const selectClassName = cn(
  "h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

export type CancelFormValues = {
  cancelReason: DelegationCancelReasonDto;
  cancelReasonNote: string;
  cancelledAt: string;
  cancellationAnnouncementAt: string;
};

type Props = {
  open: boolean;
  onClose: () => void;
  onConfirm: (values: CancelFormValues) => void | Promise<void>;
  submitting?: boolean;
};

export function DelegationCancelDialog({ open, onClose, onConfirm, submitting }: Props) {
  const [cancelReason, setCancelReason] = useState<DelegationCancelReasonDto | "">("");
  const [cancelReasonNote, setCancelReasonNote] = useState("");
  const [cancelledAt, setCancelledAt] = useState("");
  const [cancellationAnnouncementAt, setCancellationAnnouncementAt] = useState("");

  useEffect(() => {
    if (!open) return;
    setCancelReason("");
    setCancelReasonNote("");
    setCancelledAt("");
    setCancellationAnnouncementAt("");
  }, [open]);

  async function handleSubmit() {
    if (!cancelReason) return;
    await onConfirm({
      cancelReason,
      cancelReasonNote,
      cancelledAt,
      cancellationAnnouncementAt,
    });
  }

  return (
    <AnimatePresence>
      {open ? (
        <>
          <motion.div
            className="fixed inset-0 z-50 bg-black/45"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />
          <motion.div
            className="fixed inset-x-4 top-[10%] z-50 mx-auto max-w-lg rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-pop)] sm:inset-x-auto"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 12 }}
            role="dialog"
            aria-modal="true"
            aria-labelledby="cancel-delegation-title"
          >
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <h2 id="cancel-delegation-title" className="text-lg font-semibold">
                  إلغاء التوكيل
                </h2>
                <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
                  سيتغيّر الحالة إلى «ملغي». املأ بيانات الإلغاء ثم ارفع مستند الإلغاء من المرفقات.
                </p>
              </div>
              <Button type="button" variant="ghost" size="icon" onClick={onClose} aria-label="إغلاق">
                <X className="size-4" />
              </Button>
            </div>
            <div className="grid gap-4">
              <div className="space-y-2">
                <Label htmlFor="dlg-cancelReason">سبب الإلغاء *</Label>
                <select
                  id="dlg-cancelReason"
                  className={selectClassName}
                  value={cancelReason}
                  onChange={(e) => setCancelReason(e.target.value as DelegationCancelReasonDto)}
                >
                  <option value="">—</option>
                  {(Object.entries(DELEGATION_CANCEL_REASON_AR) as [DelegationCancelReasonDto, string][]).map(
                    ([k, v]) => (
                      <option key={k} value={k}>{v}</option>
                    ),
                  )}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="dlg-cancelledAt">تاريخ إلغاء الوكالة</Label>
                <Input
                  id="dlg-cancelledAt"
                  type="date"
                  value={cancelledAt}
                  onChange={(e) => setCancelledAt(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="dlg-announceAt">تاريخ الإعلان بالإلغاء</Label>
                <Input
                  id="dlg-announceAt"
                  type="date"
                  value={cancellationAnnouncementAt}
                  onChange={(e) => setCancellationAnnouncementAt(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="dlg-cancelNote">تفاصيل الإلغاء</Label>
                <Textarea
                  id="dlg-cancelNote"
                  rows={3}
                  value={cancelReasonNote}
                  onChange={(e) => setCancelReasonNote(e.target.value)}
                />
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
                تراجع
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={!cancelReason || submitting}
                onClick={() => void handleSubmit()}
              >
                {submitting ? "جاري الإلغاء..." : "تأكيد الإلغاء"}
              </Button>
            </div>
          </motion.div>
        </>
      ) : null}
    </AnimatePresence>
  );
}
