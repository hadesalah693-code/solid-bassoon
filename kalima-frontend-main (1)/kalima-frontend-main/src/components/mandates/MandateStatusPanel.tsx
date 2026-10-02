"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { MANDATE_STATUS_AR } from "@/lib/mandates/labels";
import { cn } from "@/lib/utils/cn";
import type { MandateStatusDto } from "@/types/mandate";

export type MandateStatusFormValues = {
  status: MandateStatusDto;
  cancelledReason: string;
};

export type MandateSessionFormValues = {
  didAttend: boolean;
  sessionResult: string;
  courtDecision: string;
  nextSessionDate: string;
  nextSessionNotes: string;
};

type Props = {
  open: boolean;
  currentStatus: MandateStatusDto;
  cancelled: boolean;
  onClose: () => void;
  onStatusChange: (values: MandateStatusFormValues) => void | Promise<void>;
  onSessionResult: (values: MandateSessionFormValues) => void | Promise<void>;
  submitting?: boolean;
};

const STATUS_OPTIONS = Object.keys(MANDATE_STATUS_AR) as MandateStatusDto[];

export function MandateStatusPanel({
  open,
  currentStatus,
  cancelled,
  onClose,
  onStatusChange,
  onSessionResult,
  submitting,
}: Props) {
  const [status, setStatus] = useState<MandateStatusDto>(currentStatus);
  const [cancelledReason, setCancelledReason] = useState("");
  const [didAttend, setDidAttend] = useState<boolean>(true);
  const [sessionResult, setSessionResult] = useState("");
  const [courtDecision, setCourtDecision] = useState("");
  const [nextSessionDate, setNextSessionDate] = useState("");
  const [nextSessionNotes, setNextSessionNotes] = useState("");

  useEffect(() => {
    if (!open) return;
    setStatus(currentStatus);
    setCancelledReason("");
    setSessionResult("");
    setCourtDecision("");
    setNextSessionDate("");
    setNextSessionNotes("");
  }, [open, currentStatus]);

  async function handleStatusSubmit() {
    await onStatusChange({ status, cancelledReason });
  }

  async function handleSessionSubmit() {
    await onSessionResult({
      didAttend,
      sessionResult,
      courtDecision,
      nextSessionDate,
      nextSessionNotes,
    });
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
              <h2 className="text-lg font-semibold">تحديث الإنابة</h2>
              <Button type="button" variant="ghost" size="icon" onClick={onClose}>
                <X className="size-4" />
              </Button>
            </div>

            <div className="flex-1 space-y-6 overflow-y-auto p-5">
              <div className="space-y-2">
                <Label>الحالة</Label>
                <div className="flex flex-col gap-2">
                  {STATUS_OPTIONS.map((s) => (
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
                      {MANDATE_STATUS_AR[s]}
                    </button>
                  ))}
                </div>
              </div>

              {status === "CANCELLED" ? (
                <div className="space-y-2">
                  <Label htmlFor="cancelled-reason">سبب الإلغاء *</Label>
                  <Textarea
                    id="cancelled-reason"
                    rows={3}
                    value={cancelledReason}
                    onChange={(e) => setCancelledReason(e.target.value)}
                    placeholder="بيّن سبب إلغاء الإنابة..."
                  />
                </div>
              ) : null}

              <div className="rounded-2xl border border-[var(--color-border)] p-4">
                <p className="mb-3 text-sm font-semibold">تسجيل نتيجة الجلسة</p>
                <div className="space-y-4">
                  <div className="space-y-2">
                    <Label>تم الحضور للجلسة؟</Label>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => setDidAttend(true)}
                        className={cn(
                          "flex-1 rounded-xl border px-4 py-2 text-sm font-medium transition",
                          didAttend
                            ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)]"
                            : "border-[var(--color-border)]",
                        )}
                      >
                        نعم
                      </button>
                      <button
                        type="button"
                        onClick={() => setDidAttend(false)}
                        className={cn(
                          "flex-1 rounded-xl border px-4 py-2 text-sm font-medium transition",
                          !didAttend
                            ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)]"
                            : "border-[var(--color-border)]",
                        )}
                      >
                        لا
                      </button>
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="session-result">نتيجة الجلسة</Label>
                    <Textarea
                      id="session-result"
                      rows={3}
                      value={sessionResult}
                      onChange={(e) => setSessionResult(e.target.value)}
                      placeholder="ملخص ما تم في الجلسة..."
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="court-decision">قرار المحكمة</Label>
                    <Textarea
                      id="court-decision"
                      rows={3}
                      value={courtDecision}
                      onChange={(e) => setCourtDecision(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="next-session-date">الجلسة القادمة</Label>
                    <Input
                      id="next-session-date"
                      type="datetime-local"
                      value={nextSessionDate}
                      onChange={(e) => setNextSessionDate(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="next-session-notes">ملاحظات الجلسة القادمة</Label>
                    <Textarea
                      id="next-session-notes"
                      rows={2}
                      value={nextSessionNotes}
                      onChange={(e) => setNextSessionNotes(e.target.value)}
                    />
                  </div>
                  <Button
                    type="button"
                    className="w-full"
                    disabled={submitting}
                    onClick={handleSessionSubmit}
                  >
                    حفظ نتيجة الجلسة
                  </Button>
                </div>
              </div>
            </div>

            <div className="flex gap-2 border-t border-[var(--color-border)] p-5">
              <Button type="button" variant="outline" className="flex-1" onClick={onClose}>
                إلغاء
              </Button>
              <Button
                type="button"
                className="flex-1"
                disabled={submitting || cancelled}
                onClick={handleStatusSubmit}
              >
                {submitting ? "جاري الحفظ..." : "تحديث الحالة"}
              </Button>
            </div>
          </motion.aside>
        </>
      ) : null}
    </AnimatePresence>
  );
}
