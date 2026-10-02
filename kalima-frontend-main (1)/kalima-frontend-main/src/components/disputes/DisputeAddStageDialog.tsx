"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useState } from "react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { disputesApi } from "@/lib/api/disputes";
import { ApiRequestError } from "@/lib/api/client";
import { cn } from "@/lib/utils/cn";
import type { LegalPathDto } from "@/types/dispute";

type StagePreset = {
  id: string;
  title: string;
  description: string;
  legalPath: LegalPathDto;
};

const STAGE_PRESETS: StagePreset[] = [
  {
    id: "negotiation",
    title: "تفاوض",
    description: "مسار تفاوض ودي أو ما قبل التقاضي",
    legalPath: "NEGOTIATION",
  },
  {
    id: "notice",
    title: "إنذار قانوني",
    description: "إعداد أو متابعة إنذار رسمي",
    legalPath: "LEGAL_NOTICE",
  },
  {
    id: "friendly",
    title: "تسوية ودية",
    description: "مسار تسوية خارج المحكمة",
    legalPath: "FRIENDLY_SETTLEMENT",
  },
  {
    id: "criminal",
    title: "بلاغ جنائي",
    description: "إجراءات بلاغ أو متابعة نيابة (أولى الخطوات في المسار الجنائي)",
    legalPath: "CRIMINAL_REPORT",
  },
  {
    id: "civil",
    title: "دعوى مدنية",
    description: "مسار التقاضي أمام المحاكم المدنية",
    legalPath: "COURT_CASE",
  },
  {
    id: "arbitration",
    title: "تحكيم",
    description: "مسار منازعة أمام جهة تحكيم",
    legalPath: "ARBITRATION",
  },
  {
    id: "admin",
    title: "إجراء إداري",
    description: "متابعة أمام جهات إدارية",
    legalPath: "ADMINISTRATIVE_ACTION",
  },
];

export type DisputeAddStageDialogProps = {
  open: boolean;
  disputeId: string;
  onClose: () => void;
  onSaved: () => void;
};

export function DisputeAddStageDialog({
  open,
  disputeId,
  onClose,
  onSaved,
}: DisputeAddStageDialogProps) {
  const [selected, setSelected] = useState<string | null>(
    STAGE_PRESETS[0]?.id ?? null,
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    const preset = STAGE_PRESETS.find((p) => p.id === selected);
    if (!preset) return;
    setSubmitting(true);
    setError(null);
    try {
      await disputesApi.addStage(disputeId, {
        stageType: preset.legalPath,
        status: "ACTIVE",
        title: preset.title,
        startedAt: new Date().toISOString(),
      });
      onSaved();
      onClose();
    } catch (e) {
      setError(
        e instanceof ApiRequestError
          ? e.details?.message ?? e.message
          : "تعذر حفظ المرحلة",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AnimatePresence>
      {open ? (
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
            className="relative z-10 w-full max-w-lg overflow-hidden rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-xl"
          >
            <div className="flex items-start justify-between gap-3 border-b border-[var(--color-border)] px-5 py-4">
              <div>
                <h2 className="text-base font-semibold text-[var(--color-foreground)]">
                  إضافة مرحلة
                </h2>
                <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
                  اختر نوع المرحلة لتسجيلها في سجل النزاع. يمكنك لاحقاً متابعة
                  الحالة ومرحلة سير العمل من لوحة الإعداد السريع.
                </p>
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
            <div className="max-h-[min(60vh,420px)] space-y-2 overflow-y-auto px-5 py-4">
              {error ? <ApiErrorAlert message={error} /> : null}
              <Label className="text-xs text-[var(--color-muted-foreground)]">
                نوع المرحلة
              </Label>
              <ul className="space-y-2">
                {STAGE_PRESETS.map((p) => (
                  <li key={p.id}>
                    <button
                      type="button"
                      disabled={submitting}
                      onClick={() => setSelected(p.id)}
                      className={cn(
                        "w-full rounded-xl border px-3 py-2.5 text-right text-sm transition-colors",
                        selected === p.id
                          ? "border-[var(--color-gold-400)] bg-[var(--color-gold-400)]/12 shadow-[inset_0_0_0_1px_rgba(212,175,55,0.25)]"
                          : "border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 hover:bg-[var(--color-surface-muted)]/70",
                      )}
                    >
                      <span className="font-medium text-[var(--color-foreground)]">
                        {p.title}
                      </span>
                      <p className="mt-0.5 text-xs text-[var(--color-muted-foreground)]">
                        {p.description}
                      </p>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
            <div className="flex justify-end gap-2 border-t border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 px-5 py-3">
              <Button
                type="button"
                variant="outline"
                disabled={submitting}
                onClick={onClose}
              >
                إلغاء
              </Button>
              <Button
                type="button"
                variant="gold"
                disabled={submitting || !selected}
                onClick={() => void submit()}
              >
                {submitting ? "جارٍ الحفظ…" : "تأكيد المرحلة"}
              </Button>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
