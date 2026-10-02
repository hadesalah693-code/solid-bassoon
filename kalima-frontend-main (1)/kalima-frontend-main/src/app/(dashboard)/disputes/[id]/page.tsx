"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  ArrowRight,
  FileText,
  Link2,
  MessageSquare,
  RefreshCw,
  Scale,
  UserPlus,
  GitBranch,
  Gavel,
} from "lucide-react";

import { DisputeActionsSection } from "@/components/disputes/DisputeActionsSection";
import { DisputeTasksPanel } from "@/components/disputes/DisputeTasksPanel";
import { DisputeEditCoreSection } from "@/components/disputes/DisputeEditCoreSection";
import { DisputeDocumentsPanel } from "@/components/disputes/DisputeDocumentsPanel";
import { DisputePartiesPanel } from "@/components/disputes/DisputePartiesPanel";
import { DisputeTeamPanel } from "@/components/disputes/DisputeTeamPanel";
import { DisputeAddStageDialog } from "@/components/disputes/DisputeAddStageDialog";
import { CloseDisputeStageDialog } from "@/components/disputes/CloseDisputeStageDialog";
import { DisputeLinkCaseDialog } from "@/components/disputes/DisputeLinkCaseDialog";
import { DisputeNextStageSuggestionPanel } from "@/components/disputes/DisputeNextStageSuggestionPanel";
import {
  DisputeLinkedCases,
  DisputeStageStrip,
} from "@/components/disputes/DisputeDetailSections";
import { DisputeWorkflowHorizontal } from "@/components/disputes/DisputeWorkflowHorizontal";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { DisputeSummaryCards } from "@/components/disputes/DisputeSummaryCards";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";
import { disputesApi } from "@/lib/api/disputes";
import { suggestNextStageAfterClose } from "@/lib/disputes/suggestNextStage";
import { ApiRequestError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils/format-date";
import {
  DISPUTE_STATUS_AR,
  DISPUTE_TYPE_AR,
  DISPUTE_WORKFLOW_STEP_AR,
  LEGAL_PATH_AR,
  RISK_LEVEL_AR,
  disputeAdminUpdateTypeAr,
  disputeCaseLinkRoleAr,
  disputeStageTypeAr,
} from "@/lib/disputes/labels";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import type {
  DisputeActionSummaryDto,
  DisputeAdministrativeUpdate,
  DisputeCaseLink,
  DisputeResponseDto,
  DisputeStage,
  DisputeStageActionDto,
  DisputeStatusDto,
  DisputeTimelineEventTypeDto,
  DisputeTimelineEventDto,
  DisputeWorkflowStepDto,
} from "@/types/dispute";
import type { CloseDisputeStageSuccessMeta } from "@/components/disputes/CloseDisputeStageDialog";

const selectClassName = cn(
  "h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function statusTone(s: DisputeResponseDto["status"]) {
  switch (s) {
    case "CONVERTED_TO_CASE":
    case "CLOSED":
      return "success" as const;
    case "REJECTED":
      return "danger" as const;
    case "READY_TO_CONVERT":
      return "gold" as const;
    case "NEW":
    case "UNDER_REVIEW":
      return "info" as const;
    default:
      return "neutral" as const;
  }
}

function riskTone(r: DisputeResponseDto["riskLevel"]) {
  switch (r) {
    case "LOW":
      return "neutral" as const;
    case "MEDIUM":
      return "info" as const;
    case "HIGH":
      return "warning" as const;
    case "CRITICAL":
      return "danger" as const;
  }
}

function systemEventIcon(t: DisputeTimelineEventTypeDto) {
  switch (t) {
    case "CREATED":
      return FileText;
    case "STATUS_CHANGED":
      return RefreshCw;
    case "WORKFLOW_STEP_CHANGED":
      return GitBranch;
    case "LEGAL_PATH_CHANGED":
      return Scale;
    case "LAWYER_ASSIGNED":
      return UserPlus;
    case "CONVERTED_TO_CASE":
      return Gavel;
    case "NOTE_ADDED":
      return MessageSquare;
    default:
      return FileText;
  }
}

function shortDisputeNo(id: string) {
  return id.replace(/-/g, "").slice(0, 8).toUpperCase();
}

function disputeDisplayRef(row: DisputeResponseDto) {
  const n = row.disputeNumber?.trim();
  return n || shortDisputeNo(row.id);
}

export default function DisputeDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const [row, setRow] = useState<DisputeResponseDto | null>(null);
  const [stages, setStages] = useState<DisputeStage[]>([]);
  const [linkedCases, setLinkedCases] = useState<DisputeCaseLink[]>([]);
  const [adminUpdates, setAdminUpdates] = useState<DisputeAdministrativeUpdate[]>(
    [],
  );
  const [stageActions, setStageActions] = useState<DisputeStageActionDto[]>([]);
  const [actionSummary, setActionSummary] = useState<DisputeActionSummaryDto | null>(
    null,
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [addStageOpen, setAddStageOpen] = useState(false);

  const [statusEdit, setStatusEdit] = useState<DisputeStatusDto | "">("");
  const [stepEdit, setStepEdit] = useState<DisputeWorkflowStepDto | "">("");
  const [savingMeta, setSavingMeta] = useState(false);

  const [noteTitle, setNoteTitle] = useState("");
  const [noteBody, setNoteBody] = useState("");
  const [savingNote, setSavingNote] = useState(false);

  const [converting, setConverting] = useState(false);

  const [systemLogOpen, setSystemLogOpen] = useState(false);
  const [linkCaseOpen, setLinkCaseOpen] = useState(false);
  const [unlinkingCaseId, setUnlinkingCaseId] = useState<string | null>(null);
  const [stageToCloseId, setStageToCloseId] = useState<string | null>(null);
  const [postCloseSuggestion, setPostCloseSuggestion] =
    useState<CloseDisputeStageSuccessMeta | null>(null);

  const refreshSidecar = useCallback(async () => {
    try {
      const [st, lk, up, act, sum] = await Promise.all([
        disputesApi.listStages(id),
        disputesApi.listLinkedCases(id),
        disputesApi.listAdministrativeUpdates(id),
        disputesApi.listStageActions(id),
        disputesApi.getActionSummary(id),
      ]);
      setStages(st);
      setLinkedCases(lk);
      setAdminUpdates(up);
      setStageActions(act);
      setActionSummary(sum);
    } catch (e) {
      /* Keep existing sidecar state — avoids wiping UI on transient errors. */
      notifyApiError(e, "تعذر تحديث المراحل أو القضايا أو التحديثات أو الإجراءات");
    }
  }, [id]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const d = await disputesApi.get(id);
      setRow(d);
      setStatusEdit(d.status);
      setStepEdit(d.currentStep);
      await refreshSidecar();
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setError(e.details?.message ?? e.message);
      } else {
        setError("تعذر تحميل النزاع");
      }
      setRow(null);
    } finally {
      setLoading(false);
    }
  }, [id, refreshSidecar]);

  useEffect(() => {
    load();
  }, [load]);

  const converted =
    row?.status === "CONVERTED_TO_CASE" || !!row?.convertedCaseId;

  const stageToCloseLabel = useMemo(() => {
    if (!stageToCloseId) return undefined;
    const s = stages.find((x) => x.id === stageToCloseId);
    if (!s) return undefined;
    return `${disputeStageTypeAr(s.stageType)}${s.title ? ` — ${s.title}` : ""}`;
  }, [stageToCloseId, stages]);

  const closingStage = useMemo(
    () => (stageToCloseId ? stages.find((x) => x.id === stageToCloseId) : undefined),
    [stageToCloseId, stages],
  );

  const documentStageOptions = useMemo(
    () =>
      stages.map((s) => ({
        id: s.id,
        label: `${disputeStageTypeAr(s.stageType)}${s.title ? ` — ${s.title}` : ""}`,
      })),
    [stages],
  );

  type AdminDisplayItem =
    | { kind: "api"; item: DisputeAdministrativeUpdate }
    | { kind: "timeline"; item: DisputeTimelineEventDto };

  const administrativeDisplayItems = useMemo((): AdminDisplayItem[] => {
    if (adminUpdates.length > 0) {
      return adminUpdates.map((item) => ({ kind: "api", item }));
    }
    if (!row) return [];
    return row.timeline
      .filter((e) => e.eventType === "NOTE_ADDED")
      .map((item) => ({ kind: "timeline", item }));
  }, [adminUpdates, row]);

  const excludeCaseIdsForLinkPicker = useMemo(() => {
    const set = new Set<string>();
    linkedCases.forEach((l) => set.add(l.caseId));
    if (row?.convertedCaseId) set.add(row.convertedCaseId);
    return Array.from(set);
  }, [linkedCases, row?.convertedCaseId]);

  type UnifiedActivityRow = {
    id: string;
    ts: number;
    title: string;
    description: string | null;
    meta: string;
    Icon: typeof FileText;
  };

  const unifiedActivity = useMemo((): UnifiedActivityRow[] => {
    if (!row) return [];
    const out: UnifiedActivityRow[] = [];

    for (const ev of row.timeline) {
      const Icon = systemEventIcon(ev.eventType);
      out.push({
        id: `tl-${ev.id}`,
        ts: new Date(ev.createdAt).getTime(),
        title: ev.title,
        description: ev.description,
        meta:
          formatDateTime(ev.createdAt) +
          (ev.createdBy?.fullName ? ` · ${ev.createdBy.fullName}` : ""),
        Icon,
      });
    }

    for (const u of adminUpdates) {
      out.push({
        id: `adm-${u.id}`,
        ts: new Date(u.occurredAt ?? u.createdAt).getTime(),
        title: u.title,
        description: u.description,
        meta: `${disputeAdminUpdateTypeAr(u.updateType)} · ${formatDateTime(u.occurredAt ?? u.createdAt)}${
          u.createdBy?.fullName ? ` · ${u.createdBy.fullName}` : ""
        }`,
        Icon: MessageSquare,
      });
    }

    for (const s of stages) {
      out.push({
        id: `st-a-${s.id}`,
        ts: new Date(s.createdAt).getTime(),
        title: `مرحلة جديدة: ${disputeStageTypeAr(s.stageType)}`,
        description: s.title ?? null,
        meta: formatDateTime(s.createdAt),
        Icon: GitBranch,
      });
      if (s.closedAt) {
        out.push({
          id: `st-z-${s.id}`,
          ts: new Date(s.closedAt).getTime(),
          title: `إغلاق مرحلة: ${disputeStageTypeAr(s.stageType)}`,
          description: s.title ?? null,
          meta: formatDateTime(s.closedAt),
          Icon: GitBranch,
        });
      }
    }

    for (const l of linkedCases) {
      out.push({
        id: `lnk-${l.id}`,
        ts: new Date(l.createdAt).getTime(),
        title: "ربط قضية بالنزاع",
        description: `معرّف القضية: ${l.caseId} — الدور: ${disputeCaseLinkRoleAr(l.linkRole)}`,
        meta: formatDateTime(l.createdAt),
        Icon: Link2,
      });
    }

    out.sort((a, b) => b.ts - a.ts);
    return out;
  }, [row, adminUpdates, stages, linkedCases]);

  const unlinkCase = useCallback(
    async (caseId: string) => {
      if (!row) return;
      setUnlinkingCaseId(caseId);
      try {
        await disputesApi.unlinkCase(row.id, caseId);
        await refreshSidecar();
        notifySuccess("تم إلغاء ربط القضية");
      } catch (e) {
        notifyApiError(e, "تعذر إلغاء الربط");
      } finally {
        setUnlinkingCaseId(null);
      }
    },
    [row, refreshSidecar],
  );

  async function saveStatusStep() {
    if (!row) return;
    setSavingMeta(true);
    try {
      const body: {
        status?: DisputeStatusDto;
        currentStep?: DisputeWorkflowStepDto;
      } = {};
      if (statusEdit && statusEdit !== row.status) body.status = statusEdit;
      if (stepEdit && stepEdit !== row.currentStep) body.currentStep = stepEdit;
      if (Object.keys(body).length === 0) {
        notifySuccess("لا تغييرات للحفظ");
        return;
      }
      const next = await disputesApi.patch(row.id, body);
      setRow(next);
      setStatusEdit(next.status);
      setStepEdit(next.currentStep);
      notifySuccess("تم تحديث النزاع");
    } catch (e) {
      notifyApiError(e, "تعذر تحديث النزاع");
    } finally {
      setSavingMeta(false);
    }
  }

  async function submitNote(e: React.FormEvent) {
    e.preventDefault();
    if (!row || !noteTitle.trim()) return;
    setSavingNote(true);
    try {
      await disputesApi.addAdministrativeUpdate(row.id, {
        updateType: "INTERNAL_NOTE",
        title: noteTitle.trim(),
        description: noteBody.trim() || undefined,
        occurredAt: new Date().toISOString(),
      });
      setNoteTitle("");
      setNoteBody("");
      await refreshSidecar();
      notifySuccess("تمت إضافة التحديث");
    } catch (err) {
      notifyApiError(err, "تعذر إضافة التحديث");
    } finally {
      setSavingNote(false);
    }
  }

  async function convert() {
    if (!row) return;
    setConverting(true);
    try {
      const next = await disputesApi.convertToCase(row.id);
      setRow(next);
      await refreshSidecar();
      notifySuccess("تم تحويل النزاع إلى قضية");
    } catch (e) {
      notifyApiError(e, "تعذر تحويل النزاع");
    } finally {
      setConverting(false);
    }
  }

  if (loading) {
    return <PageLoading label="جارٍ تحميل النزاع…" />;
  }

  if (error || !row) {
    return (
      <div dir="rtl" className="space-y-4">
        <PageHeader title="تفاصيل النزاع" />
        <ApiErrorAlert message={error ?? "غير موجود"} onRetry={load} />
        <Button variant="outline" asChild>
          <Link href="/disputes">العودة للقائمة</Link>
        </Button>
      </div>
    );
  }

  const STATUS_OPTS = Object.entries(DISPUTE_STATUS_AR) as [DisputeStatusDto, string][];
  const STEP_OPTS = Object.entries(DISPUTE_WORKFLOW_STEP_AR) as [
    DisputeWorkflowStepDto,
    string,
  ][];

  return (
    <div dir="rtl" className="space-y-6">
      <DisputeModuleHero
        eyebrow={`مرجع ${disputeDisplayRef(row)}`}
        title={row.title}
        description={`الخصم: ${row.opponentName}${row.claimAmount != null ? ` · المطالبة: ${row.claimAmount.toLocaleString("ar-EG")}` : ""}`}
        icon={Scale}
        badges={
          <>
            <Badge tone={statusTone(row.status)}>{DISPUTE_STATUS_AR[row.status]}</Badge>
            <Badge tone={riskTone(row.riskLevel)}>{RISK_LEVEL_AR[row.riskLevel]}</Badge>
            <Badge tone="neutral">{DISPUTE_TYPE_AR[row.disputeType]}</Badge>
          </>
        }
        actions={
          <Button
            variant="outline"
            asChild
            className="border-white/25 bg-white/10 text-white hover:bg-white/15 hover:text-white"
          >
            <Link href="/disputes" className="inline-flex items-center gap-2">
              <ArrowRight className="size-4" />
              القائمة
            </Link>
          </Button>
        }
      />

      <DisputeSummaryCards row={row} />

      <DisputePartiesPanel
        disputeId={row.id}
        canEdit={!converted}
        primaryClientId={row.client?.id ?? null}
        onChange={() => void refreshSidecar()}
      />

      <DisputeTeamPanel
        disputeId={row.id}
        initialTeam={row.team ?? []}
        canEdit={!converted}
        onSaved={() => {
          void load();
        }}
      />

      <DisputeWorkflowHorizontal
        currentStep={row.currentStep}
        stages={stages}
        activeDate={row.updatedAt}
      />

      <DisputeStageStrip
        row={row}
        disputeId={row.id}
        stageActions={stageActions}
        stages={stages}
        converted={converted}
        onAddStage={() => setAddStageOpen(true)}
        onRequestCloseStage={(sid) => setStageToCloseId(sid)}
      />

      {postCloseSuggestion && suggestNextStageAfterClose(postCloseSuggestion.stageType, postCloseSuggestion.outcome) ? (
        <DisputeNextStageSuggestionPanel
          disputeId={row.id}
          meta={postCloseSuggestion}
          onDismiss={() => setPostCloseSuggestion(null)}
          onRefresh={() => void refreshSidecar()}
        />
      ) : null}

      <DisputeActionsSection
        disputeId={row.id}
        stages={stages}
        actions={stageActions}
        summary={actionSummary}
        onRefresh={refreshSidecar}
      />

      <DisputeTasksPanel disputeId={row.id} canEdit={!converted} />

      <div id="dispute-docs" className="scroll-mt-24">
        <DisputeDocumentsPanel
          disputeId={row.id}
          canUpload={!converted}
          stages={documentStageOptions}
          onChange={() => void refreshSidecar()}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <DisputeLinkedCases
          links={linkedCases}
          legacyConvertedCaseId={row.convertedCaseId}
          onRequestLink={() => setLinkCaseOpen(true)}
          onUnlinkCase={unlinkCase}
          unlinkingCaseId={unlinkingCaseId}
        />

        <SectionCard title="إعداد سريع">
          {converted ? (
            <p className="text-sm text-[var(--color-muted-foreground)]">
              لا يمكن تعديل نزاع تم تحويله إلى قضية من هذه اللوحة.
            </p>
          ) : (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label>وضع النزاع</Label>
                <select
                  className={selectClassName}
                  value={statusEdit}
                  onChange={(e) =>
                    setStatusEdit(e.target.value as DisputeStatusDto)
                  }
                >
                  {STATUS_OPTS.map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label>المرحلة الحالية</Label>
                <select
                  className={selectClassName}
                  value={stepEdit}
                  onChange={(e) =>
                    setStepEdit(e.target.value as DisputeWorkflowStepDto)
                  }
                >
                  {STEP_OPTS.map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <Button
                type="button"
                variant="gold"
                disabled={savingMeta}
                onClick={() => void saveStatusStep()}
              >
                {savingMeta ? "جارٍ الحفظ…" : "حفظ الإعداد"}
              </Button>

              <div className="border-t border-[var(--color-border)] pt-4">
                <Button
                  type="button"
                  className="w-full"
                  disabled={converting}
                  onClick={() => void convert()}
                >
                  {converting ? "جارٍ التحويل…" : "تحويل إلى قضية"}
                </Button>
              </div>
            </div>
          )}
        </SectionCard>
      </div>

      <SectionCard
        title="التحديثات الإدارية"
        description="اجتماعات، مكالمات، مذكرات داخلية، ومتابعات يومية."
      >
        <ul className="space-y-4">
          {administrativeDisplayItems.length === 0 ? (
            <li className="text-sm text-[var(--color-muted-foreground)]">
              <p>لا تحديثات إدارية مسجّلة بعد.</p>
              <p className="mt-2 text-xs leading-relaxed">
                سجّل اجتماعاً، مكالمة، أو ملاحظة داخلية باستخدام النموذج أدناه لمتابعة الملف يومياً.
              </p>
            </li>
          ) : (
            administrativeDisplayItems.map((entry) => {
              if (entry.kind === "api") {
                const u = entry.item;
                return (
                  <li
                    key={u.id}
                    className="flex gap-3 border-b border-[var(--color-border)] pb-4 last:border-0"
                  >
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--color-gold-400)]/15 text-[var(--color-foreground)]">
                      <MessageSquare className="size-4" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="font-medium leading-snug">{u.title}</p>
                        <Badge tone="neutral" className="text-[10px]">
                          {disputeAdminUpdateTypeAr(u.updateType)}
                        </Badge>
                      </div>
                      {u.description ? (
                        <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
                          {u.description}
                        </p>
                      ) : null}
                      <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
                        {formatDateTime(u.occurredAt ?? u.createdAt)}
                        {u.createdBy?.fullName ? ` · ${u.createdBy.fullName}` : ""}
                      </p>
                    </div>
                  </li>
                );
              }
              const ev = entry.item;
              return (
                <li
                  key={ev.id}
                  className="flex gap-3 border-b border-[var(--color-border)] pb-4 last:border-0"
                >
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--color-gold-400)]/15 text-[var(--color-foreground)]">
                    <MessageSquare className="size-4" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium leading-snug">{ev.title}</p>
                      <Badge tone="neutral" className="text-[10px]">
                        ملاحظة
                      </Badge>
                    </div>
                    {ev.description ? (
                      <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
                        {ev.description}
                      </p>
                    ) : null}
                    <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
                      {formatDateTime(ev.createdAt)}
                      {ev.createdBy?.fullName ? ` · ${ev.createdBy.fullName}` : ""}
                    </p>
                  </div>
                </li>
              );
            })
          )}
        </ul>

        {!converted ? (
          <form
            onSubmit={submitNote}
            className="mt-6 space-y-3 border-t border-[var(--color-border)] pt-6"
          >
            <p className="text-sm font-medium">تسجيل تحديث جديد</p>
            <div className="space-y-2">
              <Label htmlFor="noteTitle">عنوان التحديث</Label>
              <Input
                id="noteTitle"
                placeholder="مثال: اجتماع مع الطرف الآخر"
                value={noteTitle}
                onChange={(e) => setNoteTitle(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="noteBody">التفاصيل والنتيجة</Label>
              <Textarea
                id="noteBody"
                rows={3}
                placeholder="ماذا تم الاتفاق عليه، أو ما هي الخطوة التالية…"
                value={noteBody}
                onChange={(e) => setNoteBody(e.target.value)}
              />
            </div>
            <Button type="submit" variant="gold" disabled={savingNote}>
              {savingNote ? "جارٍ الحفظ…" : "حفظ التحديث"}
            </Button>
          </form>
        ) : null}
      </SectionCard>

      <SectionCard
        title="البيانات الأساسية والوصف"
        description={
          converted
            ? "عرض فقط بعد التحويل إلى قضية."
            : "تعديل العنوان، الأطراف، النوع، المسار، المطالبة، والعميل — يتوافق مع PATCH /api/disputes/{id}."
        }
      >
        {converted ? (
          <div className="space-y-4">
            <div className="grid gap-2 sm:grid-cols-2">
              <div>
                <p className="text-xs text-[var(--color-muted-foreground)]">نوع النزاع</p>
                <p className="mt-1 text-sm font-medium">{DISPUTE_TYPE_AR[row.disputeType]}</p>
              </div>
              <div>
                <p className="text-xs text-[var(--color-muted-foreground)]">المسار القانوني</p>
                <p className="mt-1 text-sm font-medium">
                  {row.legalPath ? LEGAL_PATH_AR[row.legalPath] : "—"}
                </p>
              </div>
            </div>
            <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-sm text-[var(--color-foreground)]">
              <p className="text-xs text-[var(--color-muted-foreground)]">الوصف</p>
              <p className="mt-2 whitespace-pre-wrap">
                {row.description?.trim()
                  ? row.description
                  : "لا يوجد وصف."}
              </p>
            </div>
          </div>
        ) : (
          <DisputeEditCoreSection
            dispute={row}
            onSaved={(next) => {
              setRow(next);
              void refreshSidecar();
            }}
          />
        )}
        <div className="mt-8 border-t border-[var(--color-border)] pt-6">
          <p className="text-sm font-medium text-[var(--color-foreground)]">المرفقات</p>
          <p className="mt-2 text-xs leading-relaxed text-[var(--color-muted-foreground)]">
            رفع وعرض ومزامنة مستندات النزاع متاح أعلاً في بطاقة «مرفقات النزاع». استخدم
            الرابط المرحلي هناك لربط مستند بمرحلة محددة عند الحاجة.
          </p>
          <Link
            href="#dispute-docs"
            className="mt-3 inline-flex text-sm font-medium text-[var(--color-gold-600)] hover:underline"
          >
            انتقال إلى مرفقات النزاع
          </Link>
        </div>
      </SectionCard>

      <SectionCard
        title="سجل النشاط"
        description="الأحداث، التحديثات الإدارية، المراحل، والقضايا المرتبطة — الأحدث أولاً."
      >
        <button
          type="button"
          className="flex w-full items-center justify-between rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/25 px-4 py-3 text-sm font-medium"
          onClick={() => setSystemLogOpen((v) => !v)}
        >
          <span>{unifiedActivity.length} عنصراً في السجل</span>
          <span className="text-[var(--color-muted-foreground)]">
            {systemLogOpen ? "التفّاف" : "عرض"}
          </span>
        </button>
        {systemLogOpen ? (
          <ul className="mt-4 space-y-4">
            {unifiedActivity.length === 0 ? (
              <li className="text-sm text-[var(--color-muted-foreground)]">لا عناصر بعد.</li>
            ) : (
              unifiedActivity.map((entry) => {
                const Icon = entry.Icon;
                return (
                  <li
                    key={entry.id}
                    className="flex gap-3 border-b border-[var(--color-border)] pb-4 last:border-0"
                  >
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-[var(--color-surface-muted)] text-[var(--color-foreground)]">
                      <Icon className="size-4" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-medium leading-snug">{entry.title}</p>
                      {entry.description ? (
                        <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
                          {entry.description}
                        </p>
                      ) : null}
                      <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
                        {entry.meta}
                      </p>
                    </div>
                  </li>
                );
              })
            )}
          </ul>
        ) : null}
      </SectionCard>

      <CloseDisputeStageDialog
        open={stageToCloseId != null && !!row}
        disputeId={row?.id ?? ""}
        stageId={stageToCloseId}
        stageType={closingStage?.stageType}
        stageLabel={stageToCloseLabel}
        onClose={() => setStageToCloseId(null)}
        onClosed={(meta) => {
          void refreshSidecar();
          setStageToCloseId(null);
          if (meta && suggestNextStageAfterClose(meta.stageType, meta.outcome)) {
            setPostCloseSuggestion(meta);
          }
        }}
      />
      <DisputeLinkCaseDialog
        open={linkCaseOpen}
        disputeId={row.id}
        excludeCaseIds={excludeCaseIdsForLinkPicker}
        onClose={() => setLinkCaseOpen(false)}
        onLinked={() => {
          void refreshSidecar();
          notifySuccess("تم ربط القضية");
        }}
      />
      <DisputeAddStageDialog
        open={addStageOpen}
        disputeId={row.id}
        onClose={() => setAddStageOpen(false)}
        onSaved={() => {
          void refreshSidecar().then(() => {
            notifySuccess("تم تحديث المرحلة");
          });
        }}
      />
    </div>
  );
}
