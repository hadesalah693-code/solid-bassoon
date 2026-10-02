"use client";

import { useState } from "react";
import { Loader2, Pencil } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { hearingsApi } from "@/lib/api/hearings";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { localInputToApiDateTime } from "@/lib/utils/datetime-api";
import { formatDateTime } from "@/lib/utils/format-date";
import type { HearingResponseDto } from "@/types/api";

/**
 * Converts an ISO-like date-time string (including API `LocalDateTime`) into
 * a value accepted by `<input type="datetime-local">` (`yyyy-MM-ddTHH:mm`).
 * Returns an empty string for empty/invalid inputs.
 */
function toDateTimeLocal(value: string | null | undefined): string {
  if (!value) return "";
  // LocalDateTime: "2026-04-23T09:30" or "2026-04-23T09:30:00"
  return value.length >= 16 ? value.slice(0, 16) : value;
}

type Props = {
  caseId: string;
  hearing: HearingResponseDto;
  canEdit: boolean;
  onUpdated: (next: HearingResponseDto) => void;
};

export function CaseHearingRow({ caseId, hearing, canEdit, onUpdated }: Props) {
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [hearingDate, setHearingDate] = useState(
    toDateTimeLocal(hearing.hearingDate),
  );
  const [courtName, setCourtName] = useState(hearing.courtName);
  const [chamber, setChamber] = useState(hearing.chamber ?? "");
  const [result, setResult] = useState(hearing.result ?? "");
  const [notes, setNotes] = useState(hearing.notes ?? "");
  const [nextHearingDate, setNextHearingDate] = useState(
    toDateTimeLocal(hearing.nextHearingDate),
  );

  function resetFromProp() {
    setHearingDate(toDateTimeLocal(hearing.hearingDate));
    setCourtName(hearing.courtName);
    setChamber(hearing.chamber ?? "");
    setResult(hearing.result ?? "");
    setNotes(hearing.notes ?? "");
    setNextHearingDate(toDateTimeLocal(hearing.nextHearingDate));
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
      const updated = await hearingsApi.update(caseId, hearing.id, {
        hearingDate: apiDate,
        courtName: cn,
        chamber: chamber.trim() || null,
        result: result.trim() || null,
        notes: notes.trim() || null,
        nextHearingDate: nextHearingDate.trim()
          ? localInputToApiDateTime(nextHearingDate)
          : null,
      });
      onUpdated(updated);
      notifySuccess("تم حفظ تعديلات الجلسة");
      setEditing(false);
    } catch (e) {
      notifyApiError(e, "تعذر حفظ التعديلات");
    } finally {
      setSaving(false);
    }
  }

  if (!editing) {
    return (
      <Card className="p-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-xs text-[var(--color-muted-foreground)] ltr-nums">
              {formatDateTime(hearing.hearingDate)}
            </p>
            <p className="mt-1 font-semibold text-[var(--color-foreground)]">
              {hearing.courtName}
            </p>
            {hearing.chamber ? (
              <p className="text-sm text-[var(--color-muted-foreground)]">
                {hearing.chamber}
              </p>
            ) : null}
          </div>
          {canEdit ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => {
                resetFromProp();
                setEditing(true);
              }}
            >
              <Pencil className="size-4" />
              تعديل
            </Button>
          ) : null}
        </div>
        {hearing.result ? (
          <p className="mt-2 text-sm text-[var(--color-foreground)]">
            {hearing.result}
          </p>
        ) : null}
        {hearing.notes ? (
          <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
            {hearing.notes}
          </p>
        ) : null}
        {hearing.nextHearingDate ? (
          <p className="mt-2 text-xs text-[var(--color-navy-700)] ltr-nums">
            جلسة لاحقة: {formatDateTime(hearing.nextHearingDate)}
          </p>
        ) : null}
      </Card>
    );
  }

  return (
    <Card className="p-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1">
          <Label htmlFor={`eh-date-${hearing.id}`}>التاريخ والوقت *</Label>
          <Input
            id={`eh-date-${hearing.id}`}
            type="datetime-local"
            value={hearingDate}
            onChange={(e) => setHearingDate(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor={`eh-court-${hearing.id}`}>اسم المحكمة *</Label>
          <Input
            id={`eh-court-${hearing.id}`}
            value={courtName}
            onChange={(e) => setCourtName(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor={`eh-ch-${hearing.id}`}>الدائرة / القاعة</Label>
          <Input
            id={`eh-ch-${hearing.id}`}
            value={chamber}
            onChange={(e) => setChamber(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor={`eh-res-${hearing.id}`}>النتيجة</Label>
          <Textarea
            id={`eh-res-${hearing.id}`}
            value={result}
            onChange={(e) => setResult(e.target.value)}
            rows={2}
            className="resize-y"
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor={`eh-notes-${hearing.id}`}>ملاحظات</Label>
          <Textarea
            id={`eh-notes-${hearing.id}`}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={2}
            className="resize-y"
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor={`eh-next-${hearing.id}`}>جلسة تالية</Label>
          <Input
            id={`eh-next-${hearing.id}`}
            type="datetime-local"
            value={nextHearingDate}
            onChange={(e) => setNextHearingDate(e.target.value)}
            disabled={saving}
          />
        </div>
      </div>
      <div className="mt-4 flex flex-wrap justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => {
            resetFromProp();
            setEditing(false);
          }}
          disabled={saving}
        >
          إلغاء
        </Button>
        <Button
          type="button"
          variant="gold"
          size="sm"
          onClick={() => void submit()}
          disabled={saving}
        >
          {saving ? <Loader2 className="size-4 animate-spin" /> : "حفظ"}
        </Button>
      </div>
    </Card>
  );
}
