"use client";

import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { hearingsApi } from "@/lib/api/hearings";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { localInputToApiDateTime } from "@/lib/utils/datetime-api";

type Props = {
  caseId: string;
  defaultCourtName?: string | null;
  disabled?: boolean;
  onCreated: () => void;
};

export function CaseHearingAddForm({
  caseId,
  defaultCourtName,
  disabled,
  onCreated,
}: Props) {
  const [hearingDate, setHearingDate] = useState("");
  const [courtName, setCourtName] = useState(defaultCourtName ?? "");
  const [chamber, setChamber] = useState("");
  const [result, setResult] = useState("");
  const [notes, setNotes] = useState("");
  const [nextHearingDate, setNextHearingDate] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (defaultCourtName?.trim()) {
      setCourtName(defaultCourtName.trim());
    }
  }, [defaultCourtName]);

  if (disabled) {
    return null;
  }

  async function submit() {
    const apiDate = localInputToApiDateTime(hearingDate);
    if (!apiDate) {
      notifyError("حدد تاريخ ووقت الجلسة");
      return;
    }
    const cn = courtName.trim();
    if (!cn) {
      notifyError("أدخل اسم المحكمة");
      return;
    }
    setSaving(true);
    try {
      await hearingsApi.create(caseId, {
        hearingDate: apiDate,
        courtName: cn,
        chamber: chamber.trim() || null,
        result: result.trim() || null,
        notes: notes.trim() || null,
        nextHearingDate: nextHearingDate.trim()
          ? localInputToApiDateTime(nextHearingDate)
          : null,
      });
      setHearingDate("");
      setChamber("");
      setResult("");
      setNotes("");
      setNextHearingDate("");
      notifySuccess("تم تسجيل الجلسة");
      onCreated();
    } catch (e) {
      notifyApiError(e, "تعذر إضافة الجلسة");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-4">
      <p className="text-sm font-medium">تسجيل جلسة جديدة</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1">
          <Label htmlFor="h-date">التاريخ والوقت *</Label>
          <Input
            id="h-date"
            type="datetime-local"
            value={hearingDate}
            onChange={(e) => setHearingDate(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="h-court">اسم المحكمة *</Label>
          <Input
            id="h-court"
            value={courtName}
            onChange={(e) => setCourtName(e.target.value)}
            disabled={saving}
            placeholder="اسم المحكمة في هذه الجلسة"
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor="h-ch">الدائرة / القاعة (اختياري)</Label>
          <Input
            id="h-ch"
            value={chamber}
            onChange={(e) => setChamber(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor="h-res">النتيجة (اختياري)</Label>
          <Textarea
            id="h-res"
            value={result}
            onChange={(e) => setResult(e.target.value)}
            rows={2}
            className="resize-y"
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor="h-notes">ملاحظات (اختياري)</Label>
          <Textarea
            id="h-notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={2}
            className="resize-y"
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor="h-next">جلسة تالية (اختياري)</Label>
          <Input
            id="h-next"
            type="datetime-local"
            value={nextHearingDate}
            onChange={(e) => setNextHearingDate(e.target.value)}
            disabled={saving}
          />
        </div>
      </div>
      <Button
        type="button"
        variant="gold"
        size="sm"
        disabled={saving}
        onClick={() => void submit()}
      >
        {saving ? "…" : "حفظ الجلسة"}
      </Button>
    </div>
  );
}
