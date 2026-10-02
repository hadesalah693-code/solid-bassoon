"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowRight, Ban, CalendarDays, FileSignature, MoreVertical, Save, User } from "lucide-react";

import { DelegationActivityPanel } from "@/components/delegations/DelegationActivityPanel";
import { DelegationAgentsPicker } from "@/components/delegations/DelegationAgentsPicker";
import { DelegationCancelDialog } from "@/components/delegations/DelegationCancelDialog";
import { DelegationDocumentsPanel } from "@/components/delegations/DelegationDocumentsPanel";
import { DelegationLinkedCasesPanel } from "@/components/delegations/DelegationLinkedCasesPanel";
import { DelegationPartnersPicker } from "@/components/delegations/DelegationPartnersPicker";
import { ReminderRecipientsPicker } from "@/components/delegations/ReminderRecipientsPicker";
import { StaffMentionTextarea } from "@/components/delegations/StaffMentionTextarea";
import { DelegationQuickStats } from "@/components/delegations/DelegationQuickStats";
import { DelegationSummaryCards } from "@/components/delegations/DelegationSummaryCards";
import { DelegationStarButton } from "@/components/delegations/DelegationStarButton";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { delegationsApi } from "@/lib/api/delegations";
import { delegationAttorneysApi } from "@/lib/api/delegation-attorneys";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import {
  DELEGATION_CANCEL_REASON_AR,
  DELEGATION_DISPLAY_STATUS_AR,
  DELEGATION_STATUS_AR,
  DELEGATION_TYPE_AR,
  ISSUING_COUNTRIES,
  REMINDER_OFFSET_OPTIONS,
  displayDelegationStatus,
  statusTone,
} from "@/lib/delegations/labels";
import { buildDelegationTitle } from "@/lib/delegations/build-title";
import { STAFF_ROLES, clientDisplayName, toMentionStaff } from "@/lib/delegations/staff";
import { cn } from "@/lib/utils/cn";
import type {
  DelegationCancelReasonDto,
  DelegationResponseDto,
  DelegationStatusDto,
  DelegationTypeDto,
} from "@/types/delegation";
import type { ClientPerson } from "@/types/person";

const selectClassName = cn(
  "h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const TABS = [
  { id: "overview", label: "المعلومات", enabled: true },
  { id: "validity", label: "الصلاحية والتذكيرات", enabled: true },
  { id: "audit", label: "سجل الحركة", enabled: true },
] as const;

type TabId = (typeof TABS)[number]["id"];

function attorneyLabel(p: { person: { fullName?: string | null; id: string } }) {
  return p.person.fullName?.trim() || p.person.id;
}

export default function DelegationDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const [delegation, setDelegation] = useState<DelegationResponseDto | null>(null);
  const [staff, setStaff] = useState<ClientPerson[]>([]);
  const [attorneys, setAttorneys] = useState<{ id: string; name: string }[]>([]);
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [tab, setTab] = useState<TabId>("overview");

  const [agentIds, setAgentIds] = useState<string[]>([]);
  const [notaryNumber, setNotaryNumber] = useState("");
  const [partnerIds, setPartnerIds] = useState<string[]>([]);
  const [delegationType, setDelegationType] = useState<DelegationTypeDto>("JUDICIAL");
  const [issuingCountry, setIssuingCountry] = useState("QA");
  const [issuedAt, setIssuedAt] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [notes, setNotes] = useState("");
  const [status, setStatus] = useState<DelegationStatusDto>("ACTIVE");
  const [cancelReason, setCancelReason] = useState<DelegationCancelReasonDto | "">("");
  const [cancelReasonNote, setCancelReasonNote] = useState("");
  const [cancelledAt, setCancelledAt] = useState("");
  const [cancellationAnnouncementAt, setCancellationAnnouncementAt] = useState("");
  const [reminderOffsets, setReminderOffsets] = useState<number[]>([]);
  const [reminderRecipients, setReminderRecipients] = useState<string[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [d, attorneyRows, cRes, ...staffPages] = await Promise.all([
        delegationsApi.get(id),
        delegationAttorneysApi.list(true),
        personsApi.list({ role: "CLIENT", size: 500 }),
        ...STAFF_ROLES.map((r) => personsApi.list({ role: r, size: 200 })),
      ]);
      setDelegation(d);
      setAgentIds(d.agents?.map((a) => a.id) ?? (d.agent?.id ? [d.agent.id] : []));
      setNotaryNumber(d.notaryNumber ?? "");
      setPartnerIds(d.partners.map((p) => p.id));
      setDelegationType(d.delegationType);
      setIssuingCountry(d.issuingCountry);
      setIssuedAt(d.issuedAt);
      setExpiresAt(d.expiresAt);
      setNotes(d.notes ?? "");
      setStatus(d.status);
      setCancelReason(d.cancelReason ?? "");
      setCancelReasonNote(d.cancelReasonNote ?? "");
      setCancelledAt(d.cancelledAt ?? "");
      setCancellationAnnouncementAt(d.cancellationAnnouncementAt ?? "");
      setReminderOffsets(d.reminderOffsets ?? []);
      setReminderRecipients(d.reminderRecipientIds ?? []);
      setAttorneys(attorneyRows.map((a) => ({
        id: a.person.id,
        name: attorneyLabel(a),
      })));
      const merged = staffPages.flatMap((p) => p.content);
      const seen = new Set<string>();
      setStaff(merged.filter((p) => !seen.has(p.id) && seen.add(p.id)));
      setClients(cRes.content);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل التوكيل");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleSave() {
    if (!delegation) return;
    if (status === "CANCELLED" && !cancelReason) {
      notifyError("اختر سبب الإلغاء");
      return;
    }
    setSaving(true);
    try {
      const updated = await delegationsApi.patch(id, {
        agentIds,
        notaryNumber: notaryNumber.trim() || null,
        partnerIds,
        delegationType,
        issuingCountry,
        issuedAt,
        expiresAt,
        notes: notes || null,
        status,
        cancelReason: status === "CANCELLED" ? cancelReason || null : null,
        cancelReasonNote: status === "CANCELLED" ? cancelReasonNote || null : null,
        cancelledAt: status === "CANCELLED" ? cancelledAt || null : null,
        cancellationAnnouncementAt:
          status === "CANCELLED" ? cancellationAnnouncementAt || null : null,
        reminderOffsets,
        reminderRecipientIds: reminderRecipients,
      });
      setDelegation(updated);
      setPartnerIds(updated.partners.map((p) => p.id));
      setAgentIds(updated.agents?.map((a) => a.id) ?? []);
      setNotaryNumber(updated.notaryNumber ?? "");
      setCancelledAt(updated.cancelledAt ?? "");
      setCancellationAnnouncementAt(updated.cancellationAnnouncementAt ?? "");
      notifySuccess("تم حفظ التوكيل");
    } catch (e) {
      notifyApiError(e, "تعذّر حفظ التوكيل");
    } finally {
      setSaving(false);
    }
  }

  async function handleCancel(values: {
    cancelReason: DelegationCancelReasonDto;
    cancelReasonNote: string;
    cancelledAt: string;
    cancellationAnnouncementAt: string;
  }) {
    setCancelling(true);
    try {
      const updated = await delegationsApi.patch(id, {
        status: "CANCELLED",
        cancelReason: values.cancelReason,
        cancelReasonNote: values.cancelReasonNote || null,
        cancelledAt: values.cancelledAt || null,
        cancellationAnnouncementAt: values.cancellationAnnouncementAt || null,
      });
      setDelegation(updated);
      setStatus("CANCELLED");
      setCancelReason(values.cancelReason);
      setCancelReasonNote(values.cancelReasonNote);
      setCancelledAt(updated.cancelledAt ?? "");
      setCancellationAnnouncementAt(updated.cancellationAnnouncementAt ?? "");
      setCancelOpen(false);
      notifySuccess("تم إلغاء التوكيل");
    } catch (e) {
      notifyApiError(e, "تعذّر إلغاء التوكيل");
    } finally {
      setCancelling(false);
    }
  }

  function toggleOffset(days: number) {
    setReminderOffsets((prev) =>
      prev.includes(days) ? prev.filter((d) => d !== days) : [...prev, days].sort((a, b) => b - a),
    );
  }

  if (loading) return <PageLoading label="جاري تحميل التوكيل..." />;
  if (error || !delegation) {
    return (
      <div className="space-y-4">
        <ApiErrorAlert message={error ?? "التوكيل غير موجود"} />
        <Button asChild variant="outline">
          <Link href="/delegations">العودة للقائمة</Link>
        </Button>
      </div>
    );
  }

  const displayStatus = displayDelegationStatus(delegation.status, delegation.daysRemaining);
  const titlePreview = buildDelegationTitle(
    delegation.client?.fullName ?? "—",
    notaryNumber,
    delegation.delegationNumber,
  );
  const agentOptions = attorneys.length > 0 ? attorneys : staff.map((s) => ({
    id: s.id,
    name: clientDisplayName(s),
  }));

  return (
    <div className="space-y-6 pb-10">
      <DisputeModuleHero
        eyebrow={delegation.delegationNumber}
        title={delegation.title}
        icon={FileSignature}
        badges={
          <Badge tone={statusTone(displayStatus)}>
            {DELEGATION_DISPLAY_STATUS_AR[displayStatus]}
          </Badge>
        }
        actions={
          <>
            <DelegationStarButton
              delegationId={id}
              starred={delegation.starred}
              size="md"
              className="border border-white/20 bg-white/5 text-white hover:bg-white/10 hover:text-[var(--color-gold-300)]"
              onChange={(starred) => setDelegation({ ...delegation, starred })}
            />
            <Button
              type="button"
              className="gap-2 bg-[var(--color-gold-400)] text-[var(--color-navy-900)] hover:bg-[var(--color-gold-300)]"
              onClick={() => void handleSave()}
              disabled={saving}
            >
              <Save className="size-4" aria-hidden />
              {saving ? "جاري الحفظ..." : "حفظ"}
            </Button>
            {delegation.status !== "CANCELLED" ? (
              <Button
                type="button"
                variant="outline"
                className="gap-2 border-red-400/40 bg-red-500/10 text-white hover:bg-red-500/20"
                onClick={() => setCancelOpen(true)}
              >
                <Ban className="size-4" aria-hidden />
                إلغاء التوكيل
              </Button>
            ) : null}
            <Button asChild variant="outline" className="border-white/25 bg-white/5 text-white hover:bg-white/10">
              <Link href="/delegations">
                <ArrowRight className="ms-1 size-4" aria-hidden />
                القائمة
              </Link>
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  type="button"
                  variant="outline"
                  className="gap-2 border-white/25 bg-white/5 text-white hover:bg-white/10"
                  aria-label="المزيد"
                >
                  <MoreVertical className="size-4" aria-hidden />
                  المزيد
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {delegation.client?.id ?
                  <DropdownMenuItem asChild>
                    <Link href={`/clients/${delegation.client.id}`} className="gap-2">
                      <User className="size-4" />
                      ملف الموكل
                    </Link>
                  </DropdownMenuItem>
                : null}
                <DropdownMenuItem asChild>
                  <Link href="/calendar" className="gap-2">
                    <CalendarDays className="size-4" />
                    فتح التقويم
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link href="/delegations">العودة للقائمة</Link>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </>
        }
      />

      <DelegationSummaryCards delegation={delegation} />

      <div className="flex flex-wrap gap-2 border-b border-[var(--color-border)] pb-1">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            disabled={!t.enabled}
            onClick={() => t.enabled && setTab(t.id)}
            className={cn(
              "rounded-t-lg px-4 py-2 text-sm font-medium transition-colors",
              tab === t.id ?
                "border border-b-0 border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-foreground)]"
              : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
              !t.enabled && "cursor-not-allowed opacity-45",
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "audit" ? <DelegationActivityPanel delegationId={id} /> : null}

      {tab === "overview" ?
        <>
          <div className="grid gap-6 xl:grid-cols-[1fr_17rem]">
            <SectionCard title="المعلومات الأساسية">
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                <div className="space-y-2">
                  <Label>الموكل</Label>
                  <Input value={delegation.client?.fullName ?? "—"} readOnly disabled />
                </div>
                <div className="space-y-2 md:col-span-2 xl:col-span-3">
                  <DelegationAgentsPicker
                    attorneys={agentOptions}
                    selectedIds={agentIds}
                    onChange={setAgentIds}
                  />
                </div>
                <div className="space-y-2 md:col-span-2 xl:col-span-3">
                  <Label>عنوان التوكيل</Label>
                  <Input value={titlePreview} readOnly disabled className="bg-[var(--color-surface-muted)]/50" />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="delegationType">نوع التوكيل</Label>
                  <select
                    id="delegationType"
                    className={selectClassName}
                    value={delegationType}
                    onChange={(e) => setDelegationType(e.target.value as DelegationTypeDto)}
                  >
                    {(Object.entries(DELEGATION_TYPE_AR) as [DelegationTypeDto, string][]).map(([k, v]) => (
                      <option key={k} value={k}>{v}</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="issuingCountry">بلد الإصدار</Label>
                  <select
                    id="issuingCountry"
                    className={selectClassName}
                    value={issuingCountry}
                    onChange={(e) => setIssuingCountry(e.target.value)}
                  >
                    {ISSUING_COUNTRIES.map((c) => (
                      <option key={c.code} value={c.code}>{c.flag} {c.label}</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="notaryNumber">رقم التوثيق</Label>
                  <Input
                    id="notaryNumber"
                    placeholder="2026/123456"
                    value={notaryNumber}
                    onChange={(e) => setNotaryNumber(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="issuedAt">تاريخ الإصدار</Label>
                  <Input id="issuedAt" type="date" value={issuedAt} onChange={(e) => setIssuedAt(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="expiresAt">تاريخ الانتهاء</Label>
                  <Input id="expiresAt" type="date" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} />
                </div>
                <div className="space-y-2 md:col-span-2 xl:col-span-3">
                  <DelegationPartnersPicker
                    clients={clients}
                    excludeClientId={delegation.client?.id}
                    selectedIds={partnerIds}
                    onChange={setPartnerIds}
                  />
                </div>
                <div className="space-y-2 md:col-span-2 xl:col-span-3">
                  <Label htmlFor="notes">ملاحظات</Label>
                  <StaffMentionTextarea
                    id="notes"
                    value={notes}
                    onChange={setNotes}
                    staff={toMentionStaff(staff)}
                    rows={4}
                  />
                </div>
              </div>
            </SectionCard>
            <DelegationQuickStats delegationId={id} delegation={delegation} />
          </div>

          <div className="grid gap-6 xl:grid-cols-2">
            <DelegationDocumentsPanel
              delegationId={id}
              compact
              defaultDocumentType={status === "CANCELLED" ? "CANCELLATION" : "ORIGINAL"}
            />
            <DelegationLinkedCasesPanel delegationId={id} compact />
          </div>
        </>
      : null}

      {tab === "validity" ?
        <SectionCard title="الصلاحية والحالة">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="status">الحالة</Label>
              <select
                id="status"
                className={selectClassName}
                value={status}
                onChange={(e) => setStatus(e.target.value as DelegationStatusDto)}
              >
                {(Object.entries(DELEGATION_STATUS_AR) as [DelegationStatusDto, string][]).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
              <p className="text-xs text-[var(--color-muted-foreground)]">
                الحالة «ساري/منتهي» تُحدَّث تلقائياً من تاريخ الانتهاء ما لم يُلغَ التوكيل.
              </p>
            </div>
            {status === "CANCELLED" ?
              <>
                <div className="space-y-2">
                  <Label htmlFor="cancelReason">سبب الإلغاء *</Label>
                  <select
                    id="cancelReason"
                    className={selectClassName}
                    value={cancelReason}
                    onChange={(e) => setCancelReason(e.target.value as DelegationCancelReasonDto)}
                  >
                    <option value="">—</option>
                    {(Object.entries(DELEGATION_CANCEL_REASON_AR) as [DelegationCancelReasonDto, string][]).map(([k, v]) => (
                      <option key={k} value={k}>{v}</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="cancelledAt">تاريخ إلغاء الوكالة</Label>
                  <Input
                    id="cancelledAt"
                    type="date"
                    value={cancelledAt}
                    onChange={(e) => setCancelledAt(e.target.value)}
                  />
                  <p className="text-xs text-[var(--color-muted-foreground)]">
                    التاريخ المذكور في مستند إلغاء التوكيل.
                  </p>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="cancellationAnnouncementAt">تاريخ الإعلان بالإلغاء</Label>
                  <Input
                    id="cancellationAnnouncementAt"
                    type="date"
                    value={cancellationAnnouncementAt}
                    onChange={(e) => setCancellationAnnouncementAt(e.target.value)}
                  />
                  <p className="text-xs text-[var(--color-muted-foreground)]">
                    تاريخ إبلاغ المكتب بإلغاء التوكيل (عند الإلغاء من طرف الموكل).
                  </p>
                </div>
                <div className="space-y-2 md:col-span-2">
                  <Label htmlFor="cancelReasonNote">تفاصيل الإلغاء</Label>
                  <Textarea
                    id="cancelReasonNote"
                    rows={3}
                    value={cancelReasonNote}
                    onChange={(e) => setCancelReasonNote(e.target.value)}
                  />
                </div>
                <div className="md:col-span-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 px-4 py-3 text-sm text-[var(--color-muted-foreground)]">
                  ارفع مستند الإلغاء من تبويب «المعلومات» → المرفقات، واختر نوع المستند «مستند الإلغاء».
                </div>
              </>
            : null}
          </div>

          <div className="mt-6 border-t border-[var(--color-border)] pt-6">
            <h3 className="mb-1 text-sm font-semibold">تذكيرات الانتهاء</h3>
            <p className="mb-3 text-xs text-[var(--color-muted-foreground)]">
              يُزامَن تاريخ الانتهاء تلقائياً مع التقويم (
              <Link href="/calendar" className="text-[var(--color-gold-600)] underline-offset-2 hover:underline">
                فتح التقويم
              </Link>
              ).
            </p>
            <div className="mb-4 flex flex-wrap gap-2">
              {REMINDER_OFFSET_OPTIONS.map((days) => (
                <label
                  key={days}
                  className={cn(
                    "flex cursor-pointer items-center gap-2 rounded-xl border px-3 py-2 text-sm",
                    reminderOffsets.includes(days) ?
                      "border-[var(--color-gold-400)] bg-[var(--color-gold-50)]"
                    : "border-[var(--color-border)]",
                  )}
                >
                  <input
                    type="checkbox"
                    checked={reminderOffsets.includes(days)}
                    onChange={() => toggleOffset(days)}
                  />
                  {days} يوم قبل الانتهاء
                </label>
              ))}
            </div>
            <ReminderRecipientsPicker
              staff={toMentionStaff(staff)}
              selectedIds={reminderRecipients}
              onChange={setReminderRecipients}
            />
          </div>
        </SectionCard>
      : null}

      <DelegationCancelDialog
        open={cancelOpen}
        onClose={() => setCancelOpen(false)}
        onConfirm={handleCancel}
        submitting={cancelling}
      />
    </div>
  );
}
