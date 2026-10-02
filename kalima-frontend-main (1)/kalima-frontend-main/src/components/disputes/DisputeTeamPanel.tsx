"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Users } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils/cn";
import { disputesApi } from "@/lib/api/disputes";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  DISPUTE_TEAM_ROLE_AR,
  DISPUTE_TEAM_ROLE_ORDER,
} from "@/lib/disputes/labels";
import type { DisputeTeamMemberDto, DisputeTeamRoleDto } from "@/types/dispute";
import type { Staff } from "@/types/staff";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

function staffLabel(s: Staff) {
  return s.fullName?.trim() || s.email?.trim() || s.id;
}

function optionsForRole(
  role: DisputeTeamRoleDto,
  pool: {
    lawyers: Staff[];
    seniorLawyers: Staff[];
    admins: Staff[];
    allStaff: Staff[];
  },
): Staff[] {
  switch (role) {
    case "ASSIGNED_LAWYER":
    case "LEGAL_CONSULTANT":
    case "INITIAL_REVIEW":
      return pool.lawyers;
    case "FINAL_REVIEW":
      return pool.seniorLawyers.length > 0 ? pool.seniorLawyers : pool.lawyers;
    case "ADMINISTRATIVE_OFFICER":
    case "COORDINATION_REVIEW":
      return pool.admins;
    case "TRANSLATION_OFFICER":
      return pool.allStaff;
    default:
      return pool.allStaff;
  }
}

type Props = {
  disputeId: string;
  initialTeam?: DisputeTeamMemberDto[];
  canEdit: boolean;
  onSaved?: () => void;
};

export function DisputeTeamPanel({
  disputeId,
  initialTeam = [],
  canEdit,
  onSaved,
}: Props) {
  const [team, setTeam] = useState<DisputeTeamMemberDto[]>(initialTeam);
  const [draft, setDraft] = useState<Record<DisputeTeamRoleDto, string>>(() =>
    Object.fromEntries(
      DISPUTE_TEAM_ROLE_ORDER.map((r) => [r, ""]),
    ) as Record<DisputeTeamRoleDto, string>,
  );

  const [lawyers, setLawyers] = useState<Staff[]>([]);
  const [seniorLawyers, setSeniorLawyers] = useState<Staff[]>([]);
  const [admins, setAdmins] = useState<Staff[]>([]);
  const [allStaff, setAllStaff] = useState<Staff[]>([]);
  const [pickError, setPickError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveErr, setSaveErr] = useState<string | null>(null);

  const pool = useMemo(
    () => ({ lawyers, seniorLawyers, admins, allStaff }),
    [lawyers, seniorLawyers, admins, allStaff],
  );

  const syncDraftFromTeam = useCallback((members: DisputeTeamMemberDto[]) => {
    const next = Object.fromEntries(
      DISPUTE_TEAM_ROLE_ORDER.map((r) => [r, ""]),
    ) as Record<DisputeTeamRoleDto, string>;
    for (const m of members) {
      if (m.person?.id) next[m.role] = m.person.id;
    }
    setDraft(next);
    setTeam(members);
  }, []);

  useEffect(() => {
    syncDraftFromTeam(initialTeam);
  }, [initialTeam, syncDraftFromTeam]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setPickError(null);
      try {
        const [lRes, sRes, secRes, admRes] = await Promise.all([
          staffApi.list({ role: "LAWYER", size: 300 }),
          staffApi.list({ role: "SENIOR_LAWYER", size: 200 }),
          staffApi.list({ role: "SECRETARY", size: 200 }),
          staffApi.list({ role: "ADMIN", size: 100 }),
        ]);
        if (cancelled) return;
        const active = (rows: Staff[]) => rows.filter((p) => p.isActive);
        setLawyers(active(lRes.content));
        setSeniorLawyers(active(sRes.content));
        const adminList = [...active(admRes.content), ...active(secRes.content)];
        const adminMap = new Map(adminList.map((a) => [a.id, a]));
        setAdmins([...adminMap.values()]);
        const allMap = new Map<string, Staff>();
        for (const p of [
          ...lRes.content,
          ...sRes.content,
          ...secRes.content,
          ...admRes.content,
        ]) {
          if (p.isActive) allMap.set(p.id, p);
        }
        setAllStaff([...allMap.values()]);
      } catch (e) {
        if (cancelled) return;
        setPickError(
          e instanceof ApiRequestError
            ? e.details?.message ?? e.message
            : "تعذر تحميل قوائم الفريق",
        );
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function save() {
    setSaving(true);
    setSaveErr(null);
    try {
      const assignments = DISPUTE_TEAM_ROLE_ORDER.map((role) => ({
        role,
        personId: draft[role]?.trim() ? draft[role].trim() : null,
      }));
      const updated = await disputesApi.updateTeam(disputeId, { assignments });
      syncDraftFromTeam(updated);
      notifySuccess("تم تحديث فريق العمل");
      onSaved?.();
    } catch (e) {
      notifyApiError(e, "تعذر حفظ فريق العمل");
      if (e instanceof ApiRequestError) {
        setSaveErr(e.details?.message ?? e.message);
      } else {
        setSaveErr("تعذر حفظ فريق العمل");
      }
    } finally {
      setSaving(false);
    }
  }

  const readOnly = !canEdit;

  return (
    <SectionCard
      title="فريق العمل"
      description="تعيين المحامي المسؤول وباقي أعضاء الفريق. عند تغيير المحامي المسؤول يُرسل تنبيه للمحامي السابق والمسؤول الإداري."
      action={
        <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
          <Users className="size-5" />
        </div>
      }
    >
      {pickError ? <ApiErrorAlert message={pickError} className="mb-4" /> : null}
      {saveErr ? <ApiErrorAlert message={saveErr} className="mb-4" /> : null}

      <div className="grid gap-4 sm:grid-cols-2">
        {DISPUTE_TEAM_ROLE_ORDER.map((role) => {
          const options = optionsForRole(role, pool);
          const current = team.find((t) => t.role === role)?.person;
          const fallback =
            current?.id && !options.some((o) => o.id === current.id)
              ? current
              : null;

          return (
            <div key={role} className="space-y-2">
              <Label htmlFor={`team-${role}`}>{DISPUTE_TEAM_ROLE_AR[role]}</Label>
              <select
                id={`team-${role}`}
                className={selectClassName}
                value={draft[role]}
                disabled={readOnly || saving}
                onChange={(e) =>
                  setDraft((prev) => ({ ...prev, [role]: e.target.value }))
                }
              >
                <option value="">— لم يُحدد —</option>
                {fallback ? (
                  <option value={fallback.id}>
                    {fallback.fullName ?? fallback.id}
                  </option>
                ) : null}
                {options.map((p) => (
                  <option key={p.id} value={p.id}>
                    {staffLabel(p)}
                  </option>
                ))}
              </select>
              {role === "ASSIGNED_LAWYER" ? (
                <p className="text-xs text-[var(--color-muted-foreground)]">
                  تغيير المحامي المسؤول يرسل تنبيهاً للمحامي السابق والمسؤول الإداري.
                </p>
              ) : null}
            </div>
          );
        })}
      </div>

      {readOnly ? (
        <p className="mt-4 text-sm text-[var(--color-muted-foreground)]">
          لا يمكن تعديل الفريق بعد تحويل النزاع إلى قضية.
        </p>
      ) : (
        <div className="mt-6 flex justify-end border-t border-[var(--color-border)] pt-4">
          <Button type="button" variant="gold" disabled={saving} onClick={() => void save()}>
            {saving ? "جارٍ الحفظ…" : "حفظ فريق العمل"}
          </Button>
        </div>
      )}
    </SectionCard>
  );
}
