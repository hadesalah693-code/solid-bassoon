"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { LABOR_COMPLAINT_STATUS_AR } from "@/lib/labor-complaints/labels";
import { cn } from "@/lib/utils/cn";
import type { LaborComplaintStatusDto } from "@/types/labor-complaint";

export type LaborComplaintStatusFormValues = {
  status: LaborComplaintStatusDto;
  savedReason: string;
  referralNotes: string;
  linkedCaseId: string;
};

type Props = {
  open: boolean;
  currentStatus: LaborComplaintStatusDto;
  onClose: () => void;
  onConfirm: (values: LaborComplaintStatusFormValues) => void | Promise<void>;
  submitting?: boolean;
};

export function LaborComplaintStatusPanel({
  open,
  currentStatus,
  onClose,
  onConfirm,
  submitting,
}: Props) {
  const [status, setStatus] = useState<LaborComplaintStatusDto>(currentStatus);
  const [savedReason, setSavedReason] = useState("");
  const [referralNotes, setReferralNotes] = useState("");
  const [linkedCaseId, setLinkedCaseId] = useState("");

  useEffect(() => {
    if (!open) return;
    setStatus(currentStatus);
    setSavedReason("");
    setReferralNotes("");
    setLinkedCaseId("");
  }, [open, currentStatus]);

  async function handleSubmit() {
    await onConfirm({ status, savedReason, referralNotes, linkedCaseId });
  }

  return (
    <AnimatePresence>
      {open ? (
        <>
          <motion.button
            type="button"
            className="fixed inset-0 z-40 bg-black/40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            aria-label="إغلاق"
          />
          <motion.aside
            className="fixed inset-y-0 start-0 z-50 flex w-full max-w-md flex-col border-e border-[var(--color-border)] bg-[var(--color-surface)] shadow-xl"
            initial={{ x: "100%" }}
            animate={{ x: 0 }}
            exit={{ x: "100%" }}
            transition={{ type: "spring", damping: 28, stiffness: 320 }}
          >
            <div className="flex items-center justify-between border-b border-[var(--color-border)] px-5 py-4">
              <h2 className="text-lg font-semibold">تغيير حالة الشكوى</h2>
              <Button type="button" variant="ghost" size="icon" onClick={onClose}>
                <X className="size-4" />
              </Button>
            </div>
            <div className="flex-1 space-y-4 overflow-y-auto p-5">
              <div className="space-y-2">
                <Label>الحالة الجديدة</Label>
                <div className="flex flex-col gap-2">
                  {(Object.keys(LABOR_COMPLAINT_STATUS_AR) as LaborComplaintStatusDto[]).map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => setStatus(s)}
                      className={cn(
                        "rounded-xl border px-4 py-3 text-start text-sm font-medium transition",
                        status === s
                          ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)] text-[var(--color-navy-900)] shadow-[var(--shadow-soft)]"
                          : "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-muted-foreground)] hover:border-[var(--color-gold-400)]/40",
                      )}
                    >
                      {LABOR_COMPLAINT_STATUS_AR[s]}
                    </button>
                  ))}
                </div>
              </div>

              {status === "SAVED" ? (
                <div className="space-y-2">
                  <Label htmlFor="saved-reason">سبب الحفظ *</Label>
                  <Textarea
                    id="saved-reason"
                    rows={4}
                    value={savedReason}
                    onChange={(e) => setSavedReason(e.target.value)}
                    placeholder="تم فض النزاع في إدارة العمل خلال الفترة المحددة..."
                  />
                </div>
              ) : null}

              {status === "REFERRAL_TO_COMMITTEE" ? (
                <>
                  <div className="space-y-2">
                    <Label htmlFor="linked-case">معرّف القضية المفتوحة *</Label>
                    <Input
                      id="linked-case"
                      value={linkedCaseId}
                      onChange={(e) => setLinkedCaseId(e.target.value)}
                      placeholder="UUID القضية"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="referral-notes">ملاحظات الإحالة</Label>
                    <Textarea
                      id="referral-notes"
                      rows={3}
                      value={referralNotes}
                      onChange={(e) => setReferralNotes(e.target.value)}
                    />
                  </div>
                </>
              ) : null}
            </div>
            <div className="flex gap-2 border-t border-[var(--color-border)] p-5">
              <Button type="button" variant="outline" className="flex-1" onClick={onClose}>
                إلغاء
              </Button>
              <Button type="button" className="flex-1" disabled={submitting} onClick={handleSubmit}>
                {submitting ? "جاري الحفظ..." : "تأكيد"}
              </Button>
            </div>
          </motion.aside>
        </>
      ) : null}
    </AnimatePresence>
  );
}
