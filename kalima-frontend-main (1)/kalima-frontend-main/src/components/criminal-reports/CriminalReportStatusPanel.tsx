"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  CRIMINAL_REPORT_STATUS_AR,
  GRIEVANCE_PERIOD_UNIT_OPTIONS,
} from "@/lib/criminal-reports/labels";
import { cn } from "@/lib/utils/cn";
import type { CriminalReportStatusDto } from "@/types/criminal-report";

const selectClassName = cn(
  "h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

export type CriminalReportStatusFormValues = {
  status: CriminalReportStatusDto;
  archiveReason: string;
  grievancePeriodDays: number;
  grievanceDeadline: string;
  finalArchiveReason: string;
  decisionIssuer: string;
  decisionNumber: string;
  decisionReceivedDate: string;
  finalDecisionBasis: string;
  appealPeriodDays: number;
  finalDecisionLevel: string;
  linkedCaseId: string;
};

type Props = {
  open: boolean;
  currentStatus: CriminalReportStatusDto;
  onClose: () => void;
  onConfirm: (values: CriminalReportStatusFormValues) => void | Promise<void>;
  submitting?: boolean;
};

function addDays(isoDate: string, days: number) {
  const d = new Date(isoDate);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export function CriminalReportStatusPanel({
  open,
  currentStatus,
  onClose,
  onConfirm,
  submitting,
}: Props) {
  const [status, setStatus] = useState<CriminalReportStatusDto>(currentStatus);
  const [archiveReason, setArchiveReason] = useState("");
  const [grievancePeriod, setGrievancePeriod] = useState("30");
  const [grievanceUnit, setGrievanceUnit] = useState<(typeof GRIEVANCE_PERIOD_UNIT_OPTIONS)[number]["value"]>("DAY");
  const [grievanceDeadline, setGrievanceDeadline] = useState("");
  const [finalArchiveReason, setFinalArchiveReason] = useState("");
  const [decisionIssuer, setDecisionIssuer] = useState("");
  const [decisionNumber, setDecisionNumber] = useState("");
  const [decisionReceivedDate, setDecisionReceivedDate] = useState("");
  const [finalDecisionBasis, setFinalDecisionBasis] = useState("");
  const [appealPeriodDays, setAppealPeriodDays] = useState("");
  const [finalDecisionLevel, setFinalDecisionLevel] = useState("");
  const [linkedCaseId, setLinkedCaseId] = useState("");

  const grievancePeriodDays = useMemo(() => {
    const n = Number(grievancePeriod);
    if (!Number.isFinite(n) || n <= 0) return 0;
    if (grievanceUnit === "WEEK") return n * 7;
    if (grievanceUnit === "MONTH") return n * 30;
    return n;
  }, [grievancePeriod, grievanceUnit]);

  useEffect(() => {
    if (!open) return;
    setStatus(currentStatus);
    setArchiveReason("");
    setGrievancePeriod("30");
    setGrievanceUnit("DAY");
    setGrievanceDeadline("");
    setFinalArchiveReason("");
    setDecisionIssuer("");
    setDecisionNumber("");
    setDecisionReceivedDate("");
    setFinalDecisionBasis("");
    setAppealPeriodDays("");
    setFinalDecisionLevel("");
    setLinkedCaseId("");
  }, [open, currentStatus]);

  useEffect(() => {
    if (status !== "ARCHIVED" || grievancePeriodDays <= 0) return;
    setGrievanceDeadline(addDays(new Date().toISOString().slice(0, 10), grievancePeriodDays));
  }, [status, grievancePeriodDays]);

  async function handleSubmit() {
    await onConfirm({
      status,
      archiveReason,
      grievancePeriodDays,
      grievanceDeadline,
      finalArchiveReason,
      decisionIssuer,
      decisionNumber,
      decisionReceivedDate,
      finalDecisionBasis,
      appealPeriodDays: Number(appealPeriodDays) > 0 ? Number(appealPeriodDays) : 0,
      finalDecisionLevel,
      linkedCaseId,
    });
  }

  const title =
    status === "ARCHIVED" ? "حفظ البلاغ"
    : status === "FINAL_ARCHIVED" ? "حفظ نهائي للبلاغ"
    : status === "REFERRAL" ? "إحالة البلاغ"
    : "تحديث حالة البلاغ";

  return (
    <AnimatePresence>
      {open ? (
        <>
          <motion.div
            className="fixed inset-0 z-50 bg-black/40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />
          <motion.aside
            className="fixed inset-y-0 start-0 z-50 flex w-full max-w-md flex-col border-e border-[var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-pop)]"
            initial={{ x: "-100%" }}
            animate={{ x: 0 }}
            exit={{ x: "-100%" }}
            transition={{ type: "spring", damping: 28, stiffness: 320 }}
            role="dialog"
            aria-modal="true"
            aria-labelledby="cr-status-panel-title"
          >
            <div className="flex items-start justify-between gap-3 border-b border-[var(--color-border)] p-5">
              <div>
                <h2 id="cr-status-panel-title" className="text-lg font-semibold">
                  {title}
                </h2>
                <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
                  الحالة الحالية: {CRIMINAL_REPORT_STATUS_AR[currentStatus]}
                </p>
              </div>
              <Button type="button" variant="ghost" size="icon" onClick={onClose} aria-label="إغلاق">
                <X className="size-4" />
              </Button>
            </div>

            <div className="flex-1 space-y-4 overflow-y-auto p-5">
              <div className="space-y-2">
                <Label htmlFor="cr-status">حالة البلاغ</Label>
                <select
                  id="cr-status"
                  className={selectClassName}
                  value={status}
                  onChange={(e) => setStatus(e.target.value as CriminalReportStatusDto)}
                >
                  {Object.entries(CRIMINAL_REPORT_STATUS_AR).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </div>

              {status === "REFERRAL" ? (
                <div className="space-y-2">
                  <Label htmlFor="cr-linkedCase">معرّف القضية المرتبطة *</Label>
                  <Input
                    id="cr-linkedCase"
                    value={linkedCaseId}
                    onChange={(e) => setLinkedCaseId(e.target.value)}
                    placeholder="UUID للقضية الجنائية"
                  />
                </div>
              ) : null}

              {status === "ARCHIVED" ? (
                <>
                  <div className="space-y-2">
                    <Label htmlFor="cr-archiveReason">سبب الحفظ *</Label>
                    <Textarea
                      id="cr-archiveReason"
                      rows={4}
                      value={archiveReason}
                      onChange={(e) => setArchiveReason(e.target.value)}
                      placeholder="مثال: عدم كفاية الأدلة وعدم وجود ما يثبت الجريمة"
                    />
                  </div>
                  <div className="grid grid-cols-[1fr_auto] gap-2">
                    <div className="space-y-2">
                      <Label htmlFor="cr-grievancePeriod">مدة تقديم التظلم *</Label>
                      <Input
                        id="cr-grievancePeriod"
                        type="number"
                        min={1}
                        value={grievancePeriod}
                        onChange={(e) => setGrievancePeriod(e.target.value)}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="cr-grievanceUnit">الوحدة</Label>
                      <select
                        id="cr-grievanceUnit"
                        className={selectClassName}
                        value={grievanceUnit}
                        onChange={(e) =>
                          setGrievanceUnit(e.target.value as (typeof GRIEVANCE_PERIOD_UNIT_OPTIONS)[number]["value"])
                        }
                      >
                        {GRIEVANCE_PERIOD_UNIT_OPTIONS.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="cr-grievanceDeadline">آخر موعد لتقديم التظلم</Label>
                    <Input
                      id="cr-grievanceDeadline"
                      type="date"
                      value={grievanceDeadline}
                      onChange={(e) => setGrievanceDeadline(e.target.value)}
                    />
                  </div>
                  <div className="rounded-xl border border-sky-200/70 bg-sky-50/80 p-3 text-xs leading-relaxed text-sky-900 dark:border-sky-900/40 dark:bg-sky-950/30 dark:text-sky-100">
                    يُحسب الموعد تلقائياً من مدة التظلم وتاريخ الحفظ، ويمكن تعديله يدوياً.
                  </div>
                </>
              ) : null}

              {status === "FINAL_ARCHIVED" ? (
                <>
                  <div className="space-y-2">
                    <Label htmlFor="cr-finalReason">سبب الحفظ النهائي *</Label>
                    <Textarea
                      id="cr-finalReason"
                      rows={3}
                      value={finalArchiveReason}
                      onChange={(e) => setFinalArchiveReason(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="cr-decisionIssuer">الجهة صادر القرار</Label>
                    <Input
                      id="cr-decisionIssuer"
                      value={decisionIssuer}
                      onChange={(e) => setDecisionIssuer(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="cr-decisionNumber">رقم القرار</Label>
                    <Input
                      id="cr-decisionNumber"
                      value={decisionNumber}
                      onChange={(e) => setDecisionNumber(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="cr-decisionReceivedDate">تاريخ استلام القرار</Label>
                    <Input
                      id="cr-decisionReceivedDate"
                      type="date"
                      value={decisionReceivedDate}
                      onChange={(e) => setDecisionReceivedDate(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="cr-finalDecisionBasis">أساس القرار النهائي</Label>
                    <Textarea
                      id="cr-finalDecisionBasis"
                      rows={3}
                      value={finalDecisionBasis}
                      onChange={(e) => setFinalDecisionBasis(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="cr-appealPeriodDays">مدة الاستئناف (أيام)</Label>
                    <Input
                      id="cr-appealPeriodDays"
                      type="number"
                      min={1}
                      value={appealPeriodDays}
                      onChange={(e) => setAppealPeriodDays(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="cr-finalDecisionLevel">مرجع/درجة القرار النهائي</Label>
                    <Input
                      id="cr-finalDecisionLevel"
                      value={finalDecisionLevel}
                      onChange={(e) => setFinalDecisionLevel(e.target.value)}
                      placeholder="مثال: محكمة الجنايات / درجة أولى"
                    />
                  </div>
                </>
              ) : null}
            </div>

            <div className="flex gap-2 border-t border-[var(--color-border)] p-5">
              <Button
                type="button"
                className="flex-1"
                disabled={submitting}
                onClick={handleSubmit}
              >
                {status === "ARCHIVED" ? "تأكيد الحفظ" : "تأكيد"}
              </Button>
              <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
                إلغاء
              </Button>
            </div>
          </motion.aside>
        </>
      ) : null}
    </AnimatePresence>
  );
}
