"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useEffect, useState } from "react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { disputesApi } from "@/lib/api/disputes";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import {
  ALL_DISPUTE_ACTION_TYPES,
  DISPUTE_ACTION_PRIORITY_AR,
  DISPUTE_ACTION_TYPE_AR,
  disputeStageTypeAr,
} from "@/lib/disputes/labels";
import { cn } from "@/lib/utils/cn";
import type {
  DisputeStage,
  DisputeStageActionTypeDto,
  DisputeStageActionPriorityDto,
} from "@/types/dispute";
import type { ClientPerson } from "@/types/person";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function lawyerLabel(p: ClientPerson) {
  if (p.personType === "COMPANY") return p.companyName?.trim() || p.id;
  return p.fullName?.trim() || p.id;
}

export type DisputeActionDialogProps = {
  open: boolean;
  disputeId: string;
  stages: DisputeStage[];
  /** When set, action is tied to this stage (still shown read-only for clarity). */
  defaultStageId: string | null;
  suggestedTypes: DisputeStageActionTypeDto[];
  onClose: () => void;
  onSaved: () => void;
};

export function DisputeActionDialog({
  open,
  disputeId,
  stages,
  defaultStageId,
  suggestedTypes,
  onClose,
  onSaved,
}: DisputeActionDialogProps) {
  const [lawyers, setLawyers] = useState<ClientPerson[]>([]);
  const [loadLawyers, setLoadLawyers] = useState(false);
  const [title, setTitle] = useState("");
  const [actionType, setActionType] = useState<DisputeStageActionTypeDto>("CALL");
  const [priority, setPriority] = useState<DisputeStageActionPriorityDto>("MEDIUM");
  const [dueLocal, setDueLocal] = useState("");
  const [description, setDescription] = useState("");
  const [assigneeId, setAssigneeId] = useState("");
  const [stageChoice, setStageChoice] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setError(null);
    const firstSuggest = suggestedTypes[0] ?? "CALL";
    setActionType(firstSuggest);
    setTitle("");
    setPriority("MEDIUM");
    setDueLocal("");
    setDescription("");
    setAssigneeId("");
    setStageChoice(defaultStageId ?? "");
  }, [open, defaultStageId, suggestedTypes]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    (async () => {
      setLoadLawyers(true);
      try {
        const [lRes, sRes] = await Promise.all([
          personsApi.list({ role: "LAWYER", size: 200 }),
          personsApi.list({ role: "SENIOR_LAWYER", size: 200 }),
        ]);
        if (cancelled) return;
        const map = new Map<string, ClientPerson>();
        for (const p of [...lRes.content, ...sRes.content]) {
          if (p.active && !p.archivedAt) map.set(p.id, p);
        }
        setLawyers([...map.values()]);
      } catch {
        if (!cancelled) setLawyers([]);
      } finally {
        if (!cancelled) setLoadLawyers(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open]);

  async function submit() {
    const trimmed = title.trim();
    if (!trimmed) {
      setError("العنوان مطلوب");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const stageId =
        defaultStageId ??
        (stageChoice.trim() === "" ? null : stageChoice.trim());
      await disputesApi.addStageAction(disputeId, {
        stageId,
        actionType,
        title: trimmed,
        description: description.trim() || undefined,
        priority,
        dueAt: dueLocal ? new Date(dueLocal).toISOString() : undefined,
        assignedTo: assigneeId.trim() || undefined,
      });
      onSaved();
      onClose();
    } catch (e) {
      setError(
        e instanceof ApiRequestError
          ? e.details?.message ?? e.message
          : "تعذر حفظ الإجراء",
      );
    } finally {
      setSubmitting(false);
    }
  }

  const lockedStage = defaultStageId
    ? stages.find((s) => s.id === defaultStageId)
    : null;

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
            className="relative z-10 flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-xl"
          >
            <div className="flex shrink-0 items-start justify-between gap-3 border-b border-[var(--color-border)] px-5 py-4">
              <div>
                <h2 className="text-base font-semibold text-[var(--color-foreground)]">
                  {lockedStage
                    ? `إضافة إجراء — ${disputeStageTypeAr(lockedStage.stageType)}`
                    : "إجراء جديد"}
                </h2>
                <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
                  سجّل مهمة قابلة للتنفيذ مع موعد استحقاق ومسؤول اختياري. يُنشئ النظام تحديثاً
                  إدارياً خفيفاً عند الحفظ.
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

            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-4">
              <div className="space-y-3">
                {error ? <ApiErrorAlert message={error} /> : null}

                {suggestedTypes.length > 0 ? (
                  <div className="space-y-1">
                    <Label className="text-xs text-[var(--color-muted-foreground)]">
                      اقتراحات سريعة للنوع
                    </Label>
                    <div className="flex flex-wrap gap-1.5">
                      {suggestedTypes.map((t) => (
                        <button
                          key={t}
                          type="button"
                          disabled={submitting}
                          onClick={() => setActionType(t)}
                          className={cn(
                            "rounded-full border px-2.5 py-1 text-xs transition-colors",
                            actionType === t
                              ? "border-[var(--color-gold-400)] bg-[var(--color-gold-400)]/15"
                              : "border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 hover:bg-[var(--color-surface-muted)]/70",
                          )}
                        >
                          {DISPUTE_ACTION_TYPE_AR[t]}
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}

                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="space-y-1.5 sm:col-span-2">
                    <Label htmlFor="act-title">العنوان</Label>
                    <Input
                      id="act-title"
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                      placeholder="مثال: متابعة إرسال الإنذار"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="act-type">نوع الإجراء</Label>
                    <select
                      id="act-type"
                      className={selectClassName}
                      disabled={submitting}
                      value={actionType}
                      onChange={(e) =>
                        setActionType(e.target.value as DisputeStageActionTypeDto)
                      }
                    >
                      {ALL_DISPUTE_ACTION_TYPES.map((t) => (
                        <option key={t} value={t}>
                          {DISPUTE_ACTION_TYPE_AR[t]}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="act-priority">الأولوية</Label>
                    <select
                      id="act-priority"
                      className={selectClassName}
                      disabled={submitting}
                      value={priority}
                      onChange={(e) =>
                        setPriority(e.target.value as DisputeStageActionPriorityDto)
                      }
                    >
                      {(Object.entries(DISPUTE_ACTION_PRIORITY_AR) as [DisputeStageActionPriorityDto, string][]).map(
                        ([k, v]) => (
                          <option key={k} value={k}>
                            {v}
                          </option>
                        ),
                      )}
                    </select>
                  </div>

                  <div className="space-y-1.5 sm:col-span-2">
                    <Label htmlFor="act-due">موعد الاستحقاق (اختياري)</Label>
                    <Input
                      id="act-due"
                      type="datetime-local"
                      value={dueLocal}
                      onChange={(e) => setDueLocal(e.target.value)}
                      disabled={submitting}
                    />
                  </div>

                  <div className="space-y-1.5 sm:col-span-2">
                    <Label htmlFor="act-assign">مسؤول التنفيذ (اختياري)</Label>
                    <select
                      id="act-assign"
                      className={selectClassName}
                      disabled={submitting || loadLawyers}
                      value={assigneeId}
                      onChange={(e) => setAssigneeId(e.target.value)}
                    >
                      <option value="">— بدون تعيين —</option>
                      {lawyers.map((p) => (
                        <option key={p.id} value={p.id}>
                          {lawyerLabel(p)}
                        </option>
                      ))}
                    </select>
                  </div>

                  {!defaultStageId ? (
                    <div className="space-y-1.5 sm:col-span-2">
                      <Label htmlFor="act-stage">ربط بمرحلة (اختياري)</Label>
                      <select
                        id="act-stage"
                        className={selectClassName}
                        disabled={submitting}
                        value={stageChoice}
                        onChange={(e) => setStageChoice(e.target.value)}
                      >
                        <option value="">إجراء عام للنزاع</option>
                        {stages.map((s) => (
                          <option key={s.id} value={s.id}>
                            {disputeStageTypeAr(s.stageType)}
                            {s.title ? ` — ${s.title}` : ""}
                          </option>
                        ))}
                      </select>
                    </div>
                  ) : lockedStage ? (
                    <p className="sm:col-span-2 text-xs text-[var(--color-muted-foreground)]">
                      مرتبط بالمرحلة:{" "}
                      <span className="font-medium text-[var(--color-foreground)]">
                        {disputeStageTypeAr(lockedStage.stageType)}
                      </span>
                    </p>
                  ) : null}

                  <div className="space-y-1.5 sm:col-span-2">
                    <Label htmlFor="act-desc">تفاصيل</Label>
                    <Textarea
                      id="act-desc"
                      rows={3}
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      placeholder="ملاحظات أو خطوات تنفيذ…"
                    />
                  </div>
                </div>
              </div>
            </div>

            <div className="flex shrink-0 justify-end gap-2 border-t border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 px-5 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
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
                disabled={submitting}
                onClick={() => void submit()}
              >
                {submitting ? "جارٍ الحفظ…" : "حفظ الإجراء"}
              </Button>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
