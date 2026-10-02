"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { ArrowRight, Scale } from "lucide-react";

import { CloseDisputeStageDialog } from "@/components/disputes/CloseDisputeStageDialog";
import { DisputeActionDialog } from "@/components/disputes/DisputeActionDialog";
import { DisputeDocumentsPanel } from "@/components/disputes/DisputeDocumentsPanel";
import { DisputeNextStageSuggestionPanel } from "@/components/disputes/DisputeNextStageSuggestionPanel";
import { DisputeStageActionRow } from "@/components/disputes/DisputeStageActionRow";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { disputesApi } from "@/lib/api/disputes";
import { ApiRequestError } from "@/lib/api/client";
import {
  disputeActionTypeAr,
  disputeAdminUpdateTypeAr,
  disputeCaseLinkRoleAr,
  disputeStageOutcomeAr,
  disputeStageStatusAr,
  disputeStageTypeAr,
  suggestedDisputeActionTypes,
} from "@/lib/disputes/labels";
import { computeStageActionProgress } from "@/lib/disputes/stageActionProgress";
import {
  stageProcedureSuggestions,
  type StageProcedureSuggestion,
} from "@/lib/disputes/stageWorkspaceTemplates";
import { suggestNextStageAfterClose } from "@/lib/disputes/suggestNextStage";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { formatDateTime } from "@/lib/utils/format-date";
import type {
  CloseDisputeStageSuccessMeta,
} from "@/components/disputes/CloseDisputeStageDialog";
import type {
  DisputeAdministrativeUpdate,
  DisputeCaseLink,
  DisputeResponseDto,
  DisputeStage,
  DisputeStageActionDto,
  DisputeStageActionTypeDto,
} from "@/types/dispute";

function shortDisputeNo(id: string) {
  return id.replace(/-/g, "").slice(0, 8).toUpperCase();
}

function disputeDisplayRef(row: DisputeResponseDto) {
  const n = row.disputeNumber?.trim();
  return n || shortDisputeNo(row.id);
}

function isStageWorkspaceOpen(s: DisputeStage) {
  if (s.closedAt) return false;
  const st = s.status?.toUpperCase() ?? "";
  return st !== "CLOSED" && st !== "DONE" && st !== "CANCELLED";
}

function suggestionAlreadyAdded(
  actions: DisputeStageActionDto[],
  title: string,
): boolean {
  const t = title.trim();
  return actions.some((a) => a.title.trim() === t);
}

export default function DisputeStageWorkspacePage() {
  const params = useParams();
  const disputeId = params.id as string;
  const stageId = params.stageId as string;

  const [dispute, setDispute] = useState<DisputeResponseDto | null>(null);
  const [stages, setStages] = useState<DisputeStage[]>([]);
  const [actions, setActions] = useState<DisputeStageActionDto[]>([]);
  const [updates, setUpdates] = useState<DisputeAdministrativeUpdate[]>([]);
  const [linkedCases, setLinkedCases] = useState<DisputeCaseLink[]>([]);

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [closeStageDialogOpen, setCloseStageDialogOpen] = useState(false);
  const [postCloseSuggestion, setPostCloseSuggestion] =
    useState<CloseDisputeStageSuccessMeta | null>(null);
  const [addingTitle, setAddingTitle] = useState<string | null>(null);
  const [addingAll, setAddingAll] = useState(false);

  const [updateTitle, setUpdateTitle] = useState("");
  const [updateBody, setUpdateBody] = useState("");
  const [savingUpdate, setSavingUpdate] = useState(false);

  const refreshStageSlice = useCallback(async () => {
    try {
      const [act, up] = await Promise.all([
        disputesApi.listStageActions(disputeId, stageId),
        disputesApi.listAdministrativeUpdates(disputeId),
      ]);
      setActions(act);
      setUpdates(up);
    } catch (e) {
      notifyApiError(e, "تعذر تحديث بيانات المرحلة");
    }
  }, [disputeId, stageId]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [d, st, act, up, lk] = await Promise.all([
        disputesApi.get(disputeId),
        disputesApi.listStages(disputeId),
        disputesApi.listStageActions(disputeId, stageId),
        disputesApi.listAdministrativeUpdates(disputeId),
        disputesApi.listLinkedCases(disputeId),
      ]);
      setDispute(d);
      setStages(st);
      setActions(act);
      setUpdates(up);
      setLinkedCases(lk);
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setLoadError(e.details?.message ?? e.message);
      } else {
        setLoadError("تعذر تحميل مساحة المرحلة");
      }
      setDispute(null);
    } finally {
      setLoading(false);
    }
  }, [disputeId, stageId]);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  const stage = useMemo(
    () => stages.find((s) => s.id === stageId) ?? null,
    [stages, stageId],
  );

  const converted =
    dispute?.status === "CONVERTED_TO_CASE" || !!dispute?.convertedCaseId;

  const progress = useMemo(() => computeStageActionProgress(actions), [actions]);

  const stageUpdates = useMemo(
    () => updates.filter((u) => u.stageId === stageId),
    [updates, stageId],
  );

  const suggestions = useMemo(
    (): StageProcedureSuggestion[] =>
      stage ? stageProcedureSuggestions(stage.stageType) : [],
    [stage],
  );

  const stageDocumentContext = useMemo(() => {
    if (!stage) return [];
    return [
      {
        id: stage.id,
        label: `${disputeStageTypeAr(stage.stageType)}${stage.title ? ` — ${stage.title}` : ""}`,
      },
    ];
  }, [stage]);

  const suggestedDialogTypes = useMemo((): DisputeStageActionTypeDto[] => {
    return stage ? suggestedDisputeActionTypes(stage.stageType) : ["CALL", "DOCUMENT"];
  }, [stage]);

  async function addSuggestionOne(s: StageProcedureSuggestion) {
    if (!stage || converted) return;
    setAddingTitle(s.title);
    try {
      await disputesApi.addStageAction(disputeId, {
        stageId,
        title: s.title.trim(),
        actionType: s.actionType,
        priority: "MEDIUM",
      });
      notifySuccess("تمت إضافة الإجراء");
      await refreshStageSlice();
    } catch (e) {
      notifyApiError(e, "تعذر إضافة الإجراء المقترح");
    } finally {
      setAddingTitle(null);
    }
  }

  async function addAllSuggestions() {
    if (!stage || converted) return;
    const pending = suggestions.filter((s) => !suggestionAlreadyAdded(actions, s.title));
    if (pending.length === 0) {
      notifySuccess("جميع المقترحات مضافة بالفعل");
      return;
    }
    setAddingAll(true);
    let ok = 0;
    let failed = 0;
    for (const s of pending) {
      try {
        await disputesApi.addStageAction(disputeId, {
          stageId,
          title: s.title.trim(),
          actionType: s.actionType,
          priority: "MEDIUM",
        });
        ok++;
      } catch {
        failed++;
      }
    }
    setAddingAll(false);
    await refreshStageSlice();
    if (failed === 0) {
      notifySuccess(`تمت إضافة ${ok} مقترحاً`);
    } else {
      notifyError(
        `أُضيف ${ok} ولم يُكتمل ${failed} — راجع الصلاحيات أو حاول مجدداً`,
      );
    }
  }

  async function submitStageUpdate(e: React.FormEvent) {
    e.preventDefault();
    if (!dispute || !updateTitle.trim() || converted) return;
    setSavingUpdate(true);
    try {
      await disputesApi.addAdministrativeUpdate(disputeId, {
        updateType: "INTERNAL_NOTE",
        title: updateTitle.trim(),
        description: updateBody.trim() || undefined,
        occurredAt: new Date().toISOString(),
        stageId,
      });
      setUpdateTitle("");
      setUpdateBody("");
      notifySuccess("تم تسجيل التحديث للمرحلة");
      await refreshStageSlice();
    } catch (err) {
      notifyApiError(err, "تعذر حفظ التحديث");
    } finally {
      setSavingUpdate(false);
    }
  }

  if (loading) {
    return <PageLoading label="جارٍ تحميل مساحة المرحلة…" />;
  }

  if (loadError || !dispute) {
    return (
      <div dir="rtl" className="space-y-4">
        <PageHeader title="مساحة المرحلة" />
        <ApiErrorAlert message={loadError ?? "غير موجود"} onRetry={() => void loadAll()} />
        <Button variant="outline" asChild>
          <Link href={`/disputes/${disputeId}`}>العودة للنزاع</Link>
        </Button>
      </div>
    );
  }

  if (!stage) {
    return (
      <div dir="rtl" className="space-y-4">
        <PageHeader title="مساحة المرحلة" />
        <p className="text-sm text-[var(--color-muted-foreground)]">
          لم يُعثر على هذه المرحلة ضمن النزاع.
        </p>
        <Button variant="outline" asChild>
          <Link href={`/disputes/${disputeId}`}>العودة للنزاع</Link>
        </Button>
      </div>
    );
  }

  const stageOpen = isStageWorkspaceOpen(stage);

  const anyUpdateHasStageId = updates.some((u) => u.stageId != null);

  return (
    <div dir="rtl" className="space-y-6">
      <PageHeader
        title={disputeStageTypeAr(stage.stageType)}
        description={
          <span className="block space-y-1 text-sm font-normal text-[var(--color-muted-foreground)]">
            <span className="block text-[var(--color-foreground)]">
              {dispute.title} · مرجع النزاع: {disputeDisplayRef(dispute)}
            </span>
            <span className="block">
              العميل: {dispute.client?.fullName ?? "—"} · الخصم: {dispute.opponentName}
            </span>
            {stage.title ? (
              <span className="block font-medium text-[var(--color-foreground)]">
                عنوان المرحلة: {stage.title}
              </span>
            ) : null}
          </span>
        }
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" asChild>
              <Link href={`/disputes/${disputeId}`} className="inline-flex items-center gap-2">
                <ArrowRight className="size-4" />
                العودة للنزاع
              </Link>
            </Button>
            {stageOpen && !converted ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setCloseStageDialogOpen(true)}
              >
                إغلاق المرحلة
              </Button>
            ) : null}
          </div>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="neutral">{disputeStageStatusAr(stage.status)}</Badge>
        {stage.closedAt && stage.outcome ? (
          <Badge tone="info" className="text-[10px]">
            النتيجة: {disputeStageOutcomeAr(stage.outcome)}
          </Badge>
        ) : null}
        {stage.startedAt ? (
          <span className="text-xs text-[var(--color-muted-foreground)]">
            بدء: {formatDateTime(stage.startedAt)}
          </span>
        ) : null}
        {stage.closedAt ? (
          <span className="text-xs text-[var(--color-muted-foreground)]">
            إغلاق: {formatDateTime(stage.closedAt)}
          </span>
        ) : null}
      </div>

      {stage.closedAt && stage.outcome ? (
        <SectionCard title="ملخص نتيجة الإغلاق">
          <p className="text-sm text-[var(--color-foreground)]">
            <span className="font-medium">نتيجة المرحلة:</span>{" "}
            {disputeStageOutcomeAr(stage.outcome)}
          </p>
          {stage.outcomeNote?.trim() ? (
            <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">
              <span className="font-medium text-[var(--color-foreground)]">ملاحظات إضافية:</span>{" "}
              {stage.outcomeNote.trim()}
            </p>
          ) : null}
          <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
            يتضمن الملخص الكامل لما تم إنجازه سجل «إغلاق المرحلة» ضمن التحديثات الإدارية.
          </p>
        </SectionCard>
      ) : null}

      {postCloseSuggestion && suggestNextStageAfterClose(postCloseSuggestion.stageType, postCloseSuggestion.outcome) ? (
        <DisputeNextStageSuggestionPanel
          disputeId={disputeId}
          meta={postCloseSuggestion}
          onDismiss={() => setPostCloseSuggestion(null)}
          onRefresh={() => void loadAll()}
        />
      ) : null}

      <DisputeDocumentsPanel
        disputeId={disputeId}
        canUpload={!converted}
        stages={stageDocumentContext}
        lockStageId={stageId}
        onChange={() => void loadAll()}
      />

      <SectionCard title="ملخص التقدم في الإجراءات">
        {actions.length === 0 ? (
          <p className="text-sm text-[var(--color-muted-foreground)]">
            لم تبدأ إجراءات هذه المرحلة بعد
          </p>
        ) : (
          <div className="space-y-3">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-3">
                <p className="text-[11px] text-[var(--color-muted-foreground)]">إجمالي المسجّل</p>
                <p className="mt-1 text-lg font-semibold tabular-nums">{progress.totalAll}</p>
              </div>
              <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-3">
                <p className="text-[11px] text-[var(--color-muted-foreground)]">مكتمل</p>
                <p className="mt-1 text-lg font-semibold tabular-nums text-emerald-700 dark:text-emerald-400">
                  {progress.completed}
                </p>
              </div>
              <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-3">
                <p className="text-[11px] text-[var(--color-muted-foreground)]">مفتوح / قيد التنفيذ</p>
                <p className="mt-1 text-lg font-semibold tabular-nums">{progress.active}</p>
              </div>
              <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-3">
                <p className="text-[11px] text-[var(--color-muted-foreground)]">متأخر</p>
                <p className="mt-1 text-lg font-semibold tabular-nums text-amber-700 dark:text-amber-400">
                  {progress.overdue}
                </p>
              </div>
            </div>
            <div className="space-y-1">
              <div className="flex items-center justify-between text-xs text-[var(--color-muted-foreground)]">
                <span>نسبة الإنجاز (بدون الملغاة)</span>
                <span className="tabular-nums font-medium text-[var(--color-foreground)]">
                  {progress.percentage}%
                </span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-[var(--color-surface-muted)]">
                <div
                  className="h-full rounded-full bg-[var(--color-gold-400)] transition-[width]"
                  style={{
                    width: `${progress.totalForProgress > 0 ? progress.percentage : 0}%`,
                  }}
                />
              </div>
              <p className="text-[11px] text-[var(--color-muted-foreground)]">
                ملغاة: {progress.cancelled} · أساس النسبة: {progress.totalForProgress} إجراء غير ملغى
              </p>
            </div>
          </div>
        )}
      </SectionCard>

      <SectionCard
        title="إجراءات مقترحة"
        description="قوالب جاهزة — تُضاف كمهام مفتوحة بأولوية متوسطة."
        action={
          !converted && suggestions.length > 0 ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={addingAll}
              onClick={() => void addAllSuggestions()}
            >
              {addingAll ? "جارٍ الإضافة…" : "إضافة كل المقترحات"}
            </Button>
          ) : null
        }
      >
        <div className="grid gap-2 sm:grid-cols-2">
          {suggestions.map((s) => {
            const added = suggestionAlreadyAdded(actions, s.title);
            const busy = addingTitle === s.title;
            return (
              <div
                key={`${s.title}-${s.actionType}`}
                className="flex flex-col gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/20 p-3 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium leading-snug">{s.title}</p>
                  <Badge tone="neutral" className="mt-1 text-[10px]">
                    {disputeActionTypeAr(s.actionType)}
                  </Badge>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {added ? (
                    <Badge tone="success" className="text-[10px]">
                      مضاف
                    </Badge>
                  ) : null}
                  <Button
                    type="button"
                    size="sm"
                    variant="gold"
                    disabled={converted || added || busy || addingAll}
                    onClick={() => void addSuggestionOne(s)}
                  >
                    {busy ? "جارٍ الإضافة…" : "إضافة"}
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      </SectionCard>

      <SectionCard
        title="إجراءات المرحلة"
        action={
          <Button
            type="button"
            size="sm"
            variant="gold"
            disabled={converted}
            onClick={() => setDialogOpen(true)}
          >
            إجراء جديد
          </Button>
        }
      >
        {converted ? (
          <p className="text-sm text-[var(--color-muted-foreground)]">
            النزاع مُحوّل إلى قضية — العرض فقط لهذه المرحلة.
          </p>
        ) : null}
        {actions.length === 0 ? (
          <p className="text-sm text-[var(--color-muted-foreground)]">
            لا توجد إجراءات لهذه المرحلة بعد.
          </p>
        ) : (
          <ul className="space-y-2">
            {actions.map((a) => (
              <DisputeStageActionRow
                key={a.id}
                disputeId={disputeId}
                action={a}
                onRefresh={refreshStageSlice}
              />
            ))}
          </ul>
        )}
      </SectionCard>

      <SectionCard
        title="تحديثات المرحلة"
        description="سِجل تحديثات إدارية مرتبطة بهذه المرحلة فقط (عند توفر معرّف المرحلة من الخادم)."
      >
        {!anyUpdateHasStageId ? (
          <p className="mb-3 rounded-lg border border-dashed border-[var(--color-border)] px-3 py-2 text-xs text-[var(--color-muted-foreground)]">
            لم يُرجع الخادم أي تحديث يحمل معرّف مرحلة — لا يمكن تصفية السجل حتى يُخزَّن{" "}
            <code className="rounded bg-[var(--color-surface-muted)] px-1">stageId</code> مع التحديثات.
          </p>
        ) : null}

        <ul className="space-y-3">
          {stageUpdates.length === 0 ? (
            <li className="text-sm text-[var(--color-muted-foreground)]">
              لا تحديثات مسجّلة لهذه المرحلة بعد.
            </li>
          ) : (
            stageUpdates.map((u) => (
              <li
                key={u.id}
                className="border-b border-[var(--color-border)] pb-3 last:border-0"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium leading-snug">{u.title}</p>
                  <Badge tone="neutral" className="text-[10px]">
                    {disputeAdminUpdateTypeAr(u.updateType)}
                  </Badge>
                </div>
                {u.description ? (
                  <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">{u.description}</p>
                ) : null}
                <p className="mt-2 text-[11px] text-[var(--color-muted-foreground)]">
                  {formatDateTime(u.occurredAt ?? u.createdAt)}
                  {u.createdBy?.fullName ? ` · ${u.createdBy.fullName}` : ""}
                </p>
              </li>
            ))
          )}
        </ul>

        {!converted ? (
          <form
            onSubmit={(e) => void submitStageUpdate(e)}
            className="mt-6 space-y-3 border-t border-[var(--color-border)] pt-6"
          >
            <p className="text-sm font-medium">إضافة تحديث للمرحلة</p>
            <div className="space-y-2">
              <Label htmlFor="st-up-title">العنوان</Label>
              <Input
                id="st-up-title"
                value={updateTitle}
                onChange={(e) => setUpdateTitle(e.target.value)}
                placeholder="مثال: اتصال بالخصم"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="st-up-body">التفاصيل</Label>
              <Textarea
                id="st-up-body"
                rows={3}
                value={updateBody}
                onChange={(e) => setUpdateBody(e.target.value)}
              />
            </div>
            <Button type="submit" variant="gold" disabled={savingUpdate || !updateTitle.trim()}>
              {savingUpdate ? "جارٍ الحفظ…" : "حفظ التحديث"}
            </Button>
          </form>
        ) : null}
      </SectionCard>

      <SectionCard title="القضايا المرتبطة بالنزاع" description="سياق قراءة فقط — إدارة الربط من صفحة النزاع.">
        {linkedCases.length === 0 ? (
          <p className="text-sm text-[var(--color-muted-foreground)]">لا قضايا مرتبطة.</p>
        ) : (
          <ul className="space-y-2">
            {linkedCases.map((link) => (
              <li
                key={link.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[var(--color-border)] px-4 py-3"
              >
                <div className="flex min-w-0 items-start gap-2">
                  <Scale className="mt-0.5 size-4 shrink-0 text-[var(--color-muted-foreground)]" />
                  <div>
                    <p className="text-sm font-medium">قضية مرتبطة</p>
                    <p className="font-mono text-xs text-[var(--color-muted-foreground)]">
                      {link.caseId.slice(0, 10)}…
                    </p>
                    <p className="text-[11px] text-[var(--color-muted-foreground)]">
                      الدور: {disputeCaseLinkRoleAr(link.linkRole)}
                    </p>
                  </div>
                </div>
                <Button variant="outline" size="sm" asChild>
                  <Link href={`/cases/${link.caseId}`}>فتح القضية</Link>
                </Button>
              </li>
            ))}
          </ul>
        )}
      </SectionCard>

      <CloseDisputeStageDialog
        open={closeStageDialogOpen && !!stage}
        disputeId={disputeId}
        stageId={stageId}
        stageType={stage.stageType}
        stageLabel={`${disputeStageTypeAr(stage.stageType)}${stage.title ? ` — ${stage.title}` : ""}`}
        onClose={() => setCloseStageDialogOpen(false)}
        onClosed={(meta) => {
          void loadAll();
          if (meta && suggestNextStageAfterClose(meta.stageType, meta.outcome)) {
            setPostCloseSuggestion(meta);
          }
        }}
      />
      <DisputeActionDialog
        open={dialogOpen}
        disputeId={disputeId}
        stages={stages}
        defaultStageId={stageId}
        suggestedTypes={suggestedDialogTypes}
        onClose={() => setDialogOpen(false)}
        onSaved={() => void refreshStageSlice()}
      />
    </div>
  );
}
