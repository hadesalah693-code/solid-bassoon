"use client";

import { useEffect, useMemo, useState } from "react";
import { Archive, ArchiveRestore, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils/cn";
import { casesApi } from "@/lib/api/cases";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { caseFileFromDto } from "@/types/api";
import type { CaseFile, CaseStatus } from "@/types/case";
import type { SystemRole } from "@/types/staff";

const STATUS_LABEL: Record<CaseStatus, string> = {
  NEW: "جديد",
  IN_PROGRESS: "قيد المعالجة",
  ON_HOLD: "معلّق",
  CLOSED: "مُغلق",
  ARCHIVED: "مؤرشف",
};

// ARCHIVED is not selectable from the status dropdown — use the dedicated
// archive action (which maps to DELETE /cases/{id}).
const SELECTABLE_STATUSES: CaseStatus[] = [
  "NEW",
  "IN_PROGRESS",
  "ON_HOLD",
  "CLOSED",
];

// Backend @PreAuthorize rules, mirrored here so we never show a control that
// is guaranteed to 403. Change cautiously — keep in sync with CaseController.
const STATUS_ROLES: SystemRole[] = ["ADMIN", "SENIOR_LAWYER", "LAWYER"];
const ARCHIVE_ROLES: SystemRole[] = ["ADMIN", "SENIOR_LAWYER"];

const selectClassName = cn(
  "flex h-9 cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1.5 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

type Props = {
  caseData: CaseFile;
  onChanged: (next: CaseFile) => void;
};

export function CaseActionsBar({ caseData, onChanged }: Props) {
  const [role, setRole] = useState<SystemRole | null>(null);
  const [pendingStatus, setPendingStatus] = useState(false);
  const [pendingArchive, setPendingArchive] = useState(false);

  useEffect(() => {
    setRole(getStoredUser()?.systemRole ?? null);
  }, []);

  const canChangeStatus = useMemo(
    () => Boolean(role && STATUS_ROLES.includes(role)),
    [role],
  );
  const canArchive = useMemo(
    () => Boolean(role && ARCHIVE_ROLES.includes(role)),
    [role],
  );

  const archived = Boolean(caseData.archivedAt);

  async function onStatusChange(next: CaseStatus) {
    if (next === caseData.status) return;
    setPendingStatus(true);
    try {
      const res = await casesApi.changeStatus(caseData.id, next);
      onChanged(caseFileFromDto(res));
      notifySuccess("تم تحديث حالة القضية");
    } catch (e) {
      notifyApiError(e, "تعذر تغيير الحالة");
    } finally {
      setPendingStatus(false);
    }
  }

  async function onArchive() {
    if (
      !window.confirm(
        "أرشفة هذه القضية؟ لن تظهر في القوائم الافتراضية ولن تقبل تعديلاً حتى استرجاعها.",
      )
    ) {
      return;
    }
    setPendingArchive(true);
    try {
      await casesApi.archive(caseData.id);
      // Backend returns 204 here; re-fetch the case to show the archivedAt field.
      const fresh = await casesApi.get(caseData.id);
      onChanged(caseFileFromDto(fresh));
      notifySuccess("تمت أرشفة القضية");
    } catch (e) {
      notifyApiError(e, "تعذر أرشفة القضية");
    } finally {
      setPendingArchive(false);
    }
  }

  async function onRestore() {
    setPendingArchive(true);
    try {
      const res = await casesApi.restore(caseData.id);
      onChanged(caseFileFromDto(res));
      notifySuccess("تم استرجاع القضية");
    } catch (e) {
      notifyApiError(e, "تعذر الاسترجاع");
    } finally {
      setPendingArchive(false);
    }
  }

  if (!canChangeStatus && !canArchive) {
    return null;
  }

  return (
    <div className="flex flex-wrap items-end gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 px-4 py-3">
      {canChangeStatus && !archived ? (
        <div className="flex flex-col gap-1">
          <Label htmlFor="case-status">تغيير الحالة</Label>
          <select
            id="case-status"
            className={selectClassName}
            value={caseData.status}
            disabled={pendingStatus || archived}
            onChange={(e) => void onStatusChange(e.target.value as CaseStatus)}
          >
            {SELECTABLE_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
            {caseData.status === "ARCHIVED" ? (
              <option value="ARCHIVED" disabled>
                {STATUS_LABEL.ARCHIVED}
              </option>
            ) : null}
          </select>
        </div>
      ) : null}

      {canArchive ? (
        <div className="flex items-end gap-2 ms-auto">
          {archived ? (
            <Button
              type="button"
              variant="gold"
              size="sm"
              onClick={() => void onRestore()}
              disabled={pendingArchive}
            >
              {pendingArchive ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <ArchiveRestore className="size-4" />
              )}
              استرجاع القضية
            </Button>
          ) : (
            <Button
              type="button"
              variant="destructive"
              size="sm"
              onClick={() => void onArchive()}
              disabled={pendingArchive}
            >
              {pendingArchive ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Archive className="size-4" />
              )}
              أرشفة
            </Button>
          )}
        </div>
      ) : null}
    </div>
  );
}
