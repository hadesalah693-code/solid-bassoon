"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";
import { disputesApi } from "@/lib/api/disputes";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  DISPUTE_PARTY_CAPACITY_OPTIONS,
  DISPUTE_TYPE_AR,
  LEGAL_PATH_AR,
  RISK_LEVEL_AR,
} from "@/lib/disputes/labels";
import type {
  DisputeResponseDto,
  DisputeUpdateRequestDto,
  DisputeTypeDto,
  DisputePartyCapacityDto,
  LegalPathDto,
  RiskLevelDto,
} from "@/types/dispute";
import type { ClientPerson } from "@/types/person";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function clientLabel(p: ClientPerson) {
  if (p.personType === "COMPANY") {
    return p.companyName?.trim() || p.id;
  }
  return p.fullName?.trim() || p.id;
}

const LEGAL_PATH_OPTS = Object.entries(LEGAL_PATH_AR) as [LegalPathDto, string][];

function parseClaimInput(raw: string): number | null | undefined {
  const t = raw.trim();
  if (t === "") return undefined;
  const n = Number(t.replace(",", "."));
  return Number.isFinite(n) ? n : undefined;
}

function parseProbInput(raw: string): number | null | undefined {
  const t = raw.trim();
  if (t === "") return undefined;
  const n = parseInt(t, 10);
  if (!Number.isFinite(n)) return undefined;
  return Math.min(100, Math.max(0, n));
}

/** Builds a partial PATCH body: only sends fields whose values differ from baseline. */
function buildPatchBody(
  baseline: DisputeResponseDto,
  draft: {
    title: string;
    opponentName: string;
    description: string;
    disputeType: DisputeTypeDto;
    legalPathSel: LegalPathDto | "";
    riskLevel: RiskLevelDto;
    claimAmountRaw: string;
    successProbabilityRaw: string;
    clientId: string;
    clientRoleSel: DisputePartyCapacityDto | "";
    opponentRoleSel: DisputePartyCapacityDto | "";
  },
): DisputeUpdateRequestDto {
  const out: DisputeUpdateRequestDto = {};
  const t = draft.title.trim();
  const op = draft.opponentName.trim();
  if (t !== baseline.title) out.title = t;
  if (op !== baseline.opponentName) out.opponentName = op;

  const draftDescTrimmed = draft.description.trim();
  const baseDescTrimmed = (baseline.description ?? "").trim();
  if (draftDescTrimmed !== baseDescTrimmed) {
    out.description = draftDescTrimmed === "" ? "" : draftDescTrimmed;
  }

  if (draft.disputeType !== baseline.disputeType) {
    out.disputeType = draft.disputeType;
  }

  const baseLp = baseline.legalPath ?? null;
  const nextLp =
    draft.legalPathSel === ""
      ? null
      : (draft.legalPathSel as LegalPathDto);
  if (nextLp !== baseLp) {
    if (nextLp != null) {
      out.legalPath = nextLp;
    }
    /* مسح المسار (من قيمة إلى فراغ) لا يُطبَّق عبر PATCH حاليًا؛ الـ backend يتجاهل legalPath المرسل كـ null. */
  }

  if (draft.riskLevel !== baseline.riskLevel) {
    out.riskLevel = draft.riskLevel;
  }

  const claimParsed = parseClaimInput(draft.claimAmountRaw);
  if (claimParsed !== undefined) {
    const baseClaim = baseline.claimAmount;
    const same =
      baseClaim === null ? claimParsed === null : Number(baseClaim) === claimParsed;
    if (!same) out.claimAmount = claimParsed;
  }

  const probParsed = parseProbInput(draft.successProbabilityRaw);
  if (probParsed !== undefined) {
    const baseProb = baseline.successProbability;
    if (baseProb == null || baseProb !== probParsed) {
      out.successProbability = probParsed;
    }
  }

  const baseClient = baseline.client?.id ?? "";
  if (draft.clientId !== baseClient) {
    out.clientId = draft.clientId;
  }

  const baseClientRole = baseline.clientRole ?? null;
  const nextClientRole =
    draft.clientRoleSel === "" ? null : (draft.clientRoleSel as DisputePartyCapacityDto);
  if (nextClientRole !== baseClientRole) {
    out.clientRole = nextClientRole;
  }

  const baseOpponentRole = baseline.opponentRole ?? null;
  const nextOpponentRole =
    draft.opponentRoleSel === ""
      ? null
      : (draft.opponentRoleSel as DisputePartyCapacityDto);
  if (nextOpponentRole !== baseOpponentRole) {
    out.opponentRole = nextOpponentRole;
  }

  return out;
}

export function DisputeEditCoreSection(props: {
  dispute: DisputeResponseDto;
  disabled?: boolean;
  onSaved: (next: DisputeResponseDto) => void;
}) {
  const { dispute: row, disabled, onSaved } = props;

  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [pickError, setPickError] = useState<string | null>(null);

  const [title, setTitle] = useState(row.title);
  const [opponentName, setOpponentName] = useState(row.opponentName);
  const [description, setDescription] = useState(row.description ?? "");
  const [disputeType, setDisputeType] = useState(row.disputeType);
  const [legalPathSel, setLegalPathSel] = useState<LegalPathDto | "">(
    row.legalPath ?? "",
  );
  const [riskLevel, setRiskLevel] = useState(row.riskLevel);
  const [claimAmountRaw, setClaimAmountRaw] = useState(
    row.claimAmount != null ? String(row.claimAmount) : "",
  );
  const [successProbabilityRaw, setSuccessProbabilityRaw] = useState(
    row.successProbability != null ? String(row.successProbability) : "",
  );
  const [clientId, setClientId] = useState(row.client?.id ?? "");
  const [clientRoleSel, setClientRoleSel] = useState<DisputePartyCapacityDto | "">(
    row.clientRole ?? "",
  );
  const [opponentRoleSel, setOpponentRoleSel] = useState<DisputePartyCapacityDto | "">(
    row.opponentRole ?? "",
  );

  const [saving, setSaving] = useState(false);
  const [saveErr, setSaveErr] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setPickError(null);
      try {
        const [cRes] = await Promise.all([
          personsApi.list({ role: "CLIENT", size: 500 }),
        ]);
        if (cancelled) return;
        setClients(cRes.content.filter((p) => p.active && !p.archivedAt));
      } catch (e) {
        if (cancelled) return;
        setPickError(
          e instanceof ApiRequestError
            ? e.details?.message ?? e.message
            : "تعذر تحميل قوائم العملاء والمحامين",
        );
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const resetFromRow = useCallback(() => {
    setTitle(row.title);
    setOpponentName(row.opponentName);
    setDescription(row.description ?? "");
    setDisputeType(row.disputeType);
    setLegalPathSel(row.legalPath ?? "");
    setRiskLevel(row.riskLevel);
    setClaimAmountRaw(row.claimAmount != null ? String(row.claimAmount) : "");
    setSuccessProbabilityRaw(
      row.successProbability != null ? String(row.successProbability) : "",
    );
    setClientId(row.client?.id ?? "");
    setClientRoleSel(row.clientRole ?? "");
    setOpponentRoleSel(row.opponentRole ?? "");
    setSaveErr(null);
  }, [row]);

  useEffect(() => {
    resetFromRow();
    // اعتمد تحديثات الخادم بـ updatedAt؛ لا تعيد التعيين عند كل حقول الصف الأم لتجنب سحق التحرير المحلي.
  }, [row.id, row.updatedAt, resetFromRow]);

  const bodyPreview = useMemo(
    () =>
      buildPatchBody(row, {
        title,
        opponentName,
        description,
        disputeType,
        legalPathSel,
        riskLevel,
        claimAmountRaw,
        successProbabilityRaw,
        clientId,
        clientRoleSel,
        opponentRoleSel,
      }),
    [
      row,
      title,
      opponentName,
      description,
      disputeType,
      legalPathSel,
      riskLevel,
      claimAmountRaw,
      successProbabilityRaw,
      clientId,
      clientRoleSel,
      opponentRoleSel,
    ],
  );

  const clientFallbackOption = useMemo(() => {
    const fc = row.client;
    if (!fc?.id) return null;
    if (!clients.every((c) => c.id !== fc.id)) return null;
    return (
      <option key={`fallback-client-${fc.id}`} value={fc.id}>
        {fc.fullName ?? fc.id}
      </option>
    );
  }, [clients, row.client]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!clientId.trim()) {
      setSaveErr("اختر عميلاً للنزاع");
      return;
    }
    const body = bodyPreview;
    if (Object.keys(body).length === 0) {
      notifySuccess("لا تغييرات للحفظ");
      return;
    }

    /* Ensure claim field invalid blocks save */
    if (claimAmountRaw.trim()) {
      const c = parseClaimInput(claimAmountRaw);
      if (c === undefined) {
        setSaveErr("قيمة المطالبة غير صالحة");
        return;
      }
    }
    if (successProbabilityRaw.trim()) {
      const p = parseProbInput(successProbabilityRaw);
      if (p === undefined) {
        setSaveErr("احتمال النجاح يجب أن يكون بين 0 و 100");
        return;
      }
    }

    setSaving(true);
    setSaveErr(null);
    try {
      const next = await disputesApi.patch(row.id, body);
      onSaved(next);
      notifySuccess("تم تحديث بيانات النزاع");
    } catch (err) {
      notifyApiError(err, "تعذر حفظ التعديلات");
      if (err instanceof ApiRequestError) {
        setSaveErr(err.details?.message ?? err.message);
      } else {
        setSaveErr("تعذر حفظ التعديلات");
      }
    } finally {
      setSaving(false);
    }
  }

  const readOnly = disabled === true;

  return (
    <form className="space-y-5" onSubmit={(ev) => void submit(ev)}>
      {pickError ? <ApiErrorAlert message={pickError} /> : null}
      {saveErr ? <ApiErrorAlert message={saveErr} /> : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="ec-title">عنوان النزاع</Label>
          <Input
            id="ec-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            disabled={readOnly || saving}
            maxLength={250}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="ec-opponent">الخصم</Label>
          <Input
            id="ec-opponent"
            value={opponentName}
            onChange={(e) => setOpponentName(e.target.value)}
            disabled={readOnly || saving}
            maxLength={250}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="ec-opponent-role">صفة الخصم</Label>
          <select
            id="ec-opponent-role"
            className={selectClassName}
            value={opponentRoleSel}
            onChange={(e) =>
              setOpponentRoleSel((e.target.value || "") as DisputePartyCapacityDto | "")
            }
            disabled={readOnly || saving}
          >
            <option value="">— لم تُحدد —</option>
            {DISPUTE_PARTY_CAPACITY_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="ec-client">العميل</Label>
          <select
            id="ec-client"
            className={selectClassName}
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            disabled={readOnly || saving || (clients.length === 0 && !row.client?.id)}
          >
            <option value="">— اختر عميلاً —</option>
            {clientFallbackOption}
            {clients.map((c) => (
              <option key={c.id} value={c.id}>
                {clientLabel(c)}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="ec-client-role">صفة العميل</Label>
          <select
            id="ec-client-role"
            className={selectClassName}
            value={clientRoleSel}
            onChange={(e) =>
              setClientRoleSel((e.target.value || "") as DisputePartyCapacityDto | "")
            }
            disabled={readOnly || saving}
          >
            <option value="">— لم تُحدد —</option>
            {DISPUTE_PARTY_CAPACITY_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="ec-type">نوع النزاع</Label>
          <select
            id="ec-type"
            className={selectClassName}
            value={disputeType}
            onChange={(e) => setDisputeType(e.target.value as DisputeTypeDto)}
            disabled={readOnly || saving}
          >
            {(Object.entries(DISPUTE_TYPE_AR) as [DisputeTypeDto, string][]).map(
              ([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ),
            )}
          </select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="ec-legal">المسار القانوني</Label>
          <select
            id="ec-legal"
            className={selectClassName}
            value={legalPathSel}
            onChange={(e) =>
              setLegalPathSel((e.target.value || "") as LegalPathDto | "")
            }
            disabled={readOnly || saving}
          >
            <option value="">— لم يحدد بعد —</option>
            {LEGAL_PATH_OPTS.map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="ec-risk">مستوى الخطورة</Label>
          <select
            id="ec-risk"
            className={selectClassName}
            value={riskLevel}
            onChange={(e) => setRiskLevel(e.target.value as RiskLevelDto)}
            disabled={readOnly || saving}
          >
            {(Object.entries(RISK_LEVEL_AR) as [RiskLevelDto, string][]).map(
              ([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ),
            )}
          </select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="ec-claim">المطالبة (اختياري)</Label>
          <Input
            id="ec-claim"
            dir="ltr"
            className="text-end"
            value={claimAmountRaw}
            onChange={(e) => setClaimAmountRaw(e.target.value)}
            placeholder="مثال: 125000"
            disabled={readOnly || saving}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="ec-prob">احتمال النجاح % (اختياري)</Label>
          <Input
            id="ec-prob"
            dir="ltr"
            className="text-end"
            type="number"
            min={0}
            max={100}
            value={successProbabilityRaw}
            onChange={(e) => setSuccessProbabilityRaw(e.target.value)}
            disabled={readOnly || saving}
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="ec-desc">وصف النزاع</Label>
        <Textarea
          id="ec-desc"
          rows={6}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="صف الوقائع والطلب والخلفية…"
          disabled={readOnly || saving}
        />
      </div>

      {readOnly ? null : (
        <div className="flex flex-wrap gap-3 border-t border-[var(--color-border)] pt-4">
          <Button type="submit" variant="gold" disabled={saving}>
            {saving ? "جارٍ الحفظ…" : "حفظ التعديلات"}
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={saving}
            onClick={() => resetFromRow()}
          >
            تجاهل التغييرات
          </Button>
        </div>
      )}
    </form>
  );
}
