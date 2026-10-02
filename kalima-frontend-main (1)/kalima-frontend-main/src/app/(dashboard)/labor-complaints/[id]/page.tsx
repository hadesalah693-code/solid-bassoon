"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ArrowRight, HardHat, Plus, Save, Trash2, Upload, Workflow } from "lucide-react";

import { LaborComplaintSectionNav } from "@/components/labor-complaints/LaborComplaintSectionNav";
import { LaborComplaintStatusPanel } from "@/components/labor-complaints/LaborComplaintStatusPanel";
import type { LaborComplaintStatusFormValues } from "@/components/labor-complaints/LaborComplaintStatusPanel";
import { LaborComplaintSummaryCards } from "@/components/labor-complaints/LaborComplaintSummaryCards";
import { LaborComplaintWorkflowStepper } from "@/components/labor-complaints/LaborComplaintWorkflowStepper";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { laborComplaintsApi } from "@/lib/api/labor-complaints";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  DISPUTE_TEAM_ROLE_AR,
  LABOR_COMPLAINT_PARTY_CAPACITY_OPTIONS,
  LABOR_COMPLAINT_STATUS_AR,
} from "@/lib/labor-complaints/labels";
import { STAFF_ROLES, clientDisplayName } from "@/lib/delegations/staff";
import { cn } from "@/lib/utils/cn";
import type {
  LaborComplaintDocumentDto,
  LaborComplaintMolSessionDto,
  LaborComplaintPartyCapacityDto,
  LaborComplaintResponseDto,
} from "@/types/labor-complaint";
import type { ClientPerson } from "@/types/person";
import { useParams } from "next/navigation";

const selectClassName = cn(
  "h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

type ClientRow = { clientId: string; capacity: LaborComplaintPartyCapacityDto | "" };
type OpponentRow = { name: string; capacity: LaborComplaintPartyCapacityDto | "" };

export default function LaborComplaintDetailPage() {
  const params = useParams();
  const id = params.id as string;
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [complaint, setComplaint] = useState<LaborComplaintResponseDto | null>(null);
  const [documents, setDocuments] = useState<LaborComplaintDocumentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [statusPanelOpen, setStatusPanelOpen] = useState(false);
  const [statusSubmitting, setStatusSubmitting] = useState(false);

  const [registrationDate, setRegistrationDate] = useState("");
  const [receiptDate, setReceiptDate] = useState("");
  const [complaintNumber, setComplaintNumber] = useState("");
  const [subject, setSubject] = useState("");
  const [summary, setSummary] = useState("");
  const [legalRequest, setLegalRequest] = useState("");
  const [internalReference, setInternalReference] = useState("");
  const [externalPartners, setExternalPartners] = useState("");
  const [opponentRepresentative, setOpponentRepresentative] = useState("");
  const [cooperatingOffices, setCooperatingOffices] = useState("");
  const [assignedLawyerId, setAssignedLawyerId] = useState("");
  const [clientRows, setClientRows] = useState<ClientRow[]>([{ clientId: "", capacity: "" }]);
  const [opponentRows, setOpponentRows] = useState<OpponentRow[]>([{ name: "", capacity: "" }]);
  const [molSessions, setMolSessions] = useState<LaborComplaintMolSessionDto[]>([]);
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [lawyers, setLawyers] = useState<ClientPerson[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [data, docs] = await Promise.all([
        laborComplaintsApi.get(id),
        laborComplaintsApi.listDocuments(id),
      ]);
      setComplaint(data);
      setDocuments(docs);
      setRegistrationDate(data.registrationDate);
      setReceiptDate(data.receiptDate);
      setComplaintNumber(data.complaintNumber);
      setSubject(data.subject ?? "");
      setSummary(data.summary ?? "");
      setLegalRequest(data.legalRequest ?? "");
      setInternalReference(data.internalReference ?? "");
      setExternalPartners(data.externalPartners ?? "");
      setOpponentRepresentative(data.opponentRepresentative ?? "");
      setCooperatingOffices(data.cooperatingOffices ?? "");
      setAssignedLawyerId(data.assignedLawyer?.id ?? "");
      setClientRows(
        data.clients?.length > 0
          ? data.clients.map((c) => ({
              clientId: c.client.id,
              capacity: c.capacity ?? "",
            }))
          : [{ clientId: data.client?.id ?? "", capacity: "" }],
      );
      setOpponentRows(
        data.opponents?.length > 0
          ? data.opponents.map((o) => ({ name: o.name, capacity: o.capacity ?? "" }))
          : [{ name: data.opponentName ?? "", capacity: data.opponentCapacity ?? "" }],
      );
      setMolSessions(data.molSessions ?? []);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل الشكوى");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [clientsRes, ...staffPages] = await Promise.all([
          personsApi.list({ role: "CLIENT", size: 500 }),
          ...STAFF_ROLES.map((r) => personsApi.list({ role: r, size: 200 })),
        ]);
        if (!cancelled) {
          setClients(clientsRes.content);
          const merged = staffPages.flatMap((p) => p.content);
          const seen = new Set<string>();
          setLawyers(merged.filter((p) => !seen.has(p.id) && seen.add(p.id)));
        }
      } catch {
        /* optional */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const opponents = opponentRows
        .filter((r) => r.name.trim())
        .map((r) => ({ name: r.name.trim(), capacity: r.capacity || null }));
      const updated = await laborComplaintsApi.update(id, {
        registrationDate,
        receiptDate,
        complaintNumber: complaintNumber.trim() || undefined,
        subject: subject || null,
        summary: summary || null,
        legalRequest: legalRequest || null,
        internalReference: internalReference || null,
        externalPartners: externalPartners || null,
        opponentRepresentative: opponentRepresentative || null,
        cooperatingOffices: cooperatingOffices || null,
        assignedLawyerId: assignedLawyerId || null,
        clients: clientRows
          .filter((r) => r.clientId)
          .map((r) => ({ clientId: r.clientId, capacity: r.capacity || null })),
        opponents,
        molSessions,
      });
      setComplaint(updated);
      notifySuccess("تم حفظ الشكوى");
    } catch (e) {
      notifyApiError(e, "تعذّر الحفظ");
      setError(e instanceof ApiRequestError ? e.message : "تعذّر الحفظ");
    } finally {
      setSaving(false);
    }
  }

  async function handleStatusConfirm(values: LaborComplaintStatusFormValues) {
    setStatusSubmitting(true);
    try {
      const updated = await laborComplaintsApi.updateStatus(id, {
        status: values.status,
        savedReason: values.savedReason || null,
        referralNotes: values.referralNotes || null,
        linkedCaseId: values.linkedCaseId || null,
      });
      setComplaint(updated);
      setStatusPanelOpen(false);
      notifySuccess("تم تحديث الحالة");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذّر تحديث الحالة");
    } finally {
      setStatusSubmitting(false);
    }
  }

  async function handleUpload(file: File) {
    setUploading(true);
    try {
      await laborComplaintsApi.uploadDocument(id, file);
      notifySuccess("تم رفع الملف");
      const docs = await laborComplaintsApi.listDocuments(id);
      setDocuments(docs);
    } catch (e) {
      notifyApiError(e, "تعذّر رفع الملف");
    } finally {
      setUploading(false);
    }
  }

  if (loading) return <PageLoading label="جاري تحميل الشكوى..." />;
  if (!complaint) return <ApiErrorAlert message={error ?? "الشكوى غير موجودة"} />;

  return (
    <div dir="rtl" className="space-y-6 pb-24 lg:pb-6">
      <DisputeModuleHero
        icon={HardHat}
        eyebrow="الشكاوى العمالية"
        title={complaint.complaintNumber}
        description={
          <div className="space-y-1">
            {complaint.subject?.trim() ? <p>{complaint.subject}</p> : null}
            <p className="text-white/65">
              {[
                `سنة ${complaint.complaintYear}`,
                complaint.internalReference?.trim() ? `مرجع: ${complaint.internalReference}` : null,
              ]
                .filter(Boolean)
                .join(" · ")}
            </p>
          </div>
        }
        badges={
          <span className="inline-flex items-center rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-white ring-1 ring-white/20">
            {LABOR_COMPLAINT_STATUS_AR[complaint.status]}
          </span>
        }
        actions={
          <>
            <Button
              variant="outline"
              asChild
              className="border-white/25 bg-white/5 text-white hover:bg-white/15 hover:text-white"
            >
              <Link href="/labor-complaints">
                <ArrowRight className="size-4" />
                القائمة
              </Link>
            </Button>
            <Button
              variant="outline"
              onClick={() => setStatusPanelOpen(true)}
              className="border-white/25 bg-white/5 text-white hover:bg-white/15 hover:text-white"
            >
              <Workflow className="size-4" />
              حالة الشكوى
            </Button>
            <Button
              onClick={handleSave}
              disabled={saving}
              className="hidden border-white/25 bg-[var(--color-gold-400)] text-[var(--color-navy-950)] hover:bg-[var(--color-gold-300)] sm:inline-flex"
            >
              <Save className="size-4" />
              {saving ? "جاري الحفظ..." : "حفظ التعديلات"}
            </Button>
          </>
        }
      />

      {error ? <ApiErrorAlert message={error} /> : null}

      <LaborComplaintSummaryCards complaint={complaint} />

      <LaborComplaintWorkflowStepper status={complaint.status} />

      <LaborComplaintSectionNav />

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="space-y-6">
          <div id="lc-details" className="scroll-mt-28">
          <SectionCard title="تفاصيل الشكوى" description="البيانات الإجرائية والمراجع">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>رقم الشكوى</Label>
                <Input value={complaintNumber} onChange={(e) => setComplaintNumber(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label>السنة</Label>
                <Input value={String(complaint.complaintYear)} readOnly disabled />
              </div>
              <div className="space-y-2">
                <Label>تاريخ التسجيل</Label>
                <Input type="date" value={registrationDate} onChange={(e) => setRegistrationDate(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label>تاريخ الاستلام</Label>
                <Input type="date" value={receiptDate} onChange={(e) => setReceiptDate(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label>المرجع الداخلي</Label>
                <Input value={internalReference} onChange={(e) => setInternalReference(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label>المحامي المسؤول</Label>
                <select
                  className={selectClassName}
                  value={assignedLawyerId}
                  onChange={(e) => setAssignedLawyerId(e.target.value)}
                >
                  <option value="">—</option>
                  {lawyers.map((l) => (
                    <option key={l.id} value={l.id}>
                      {clientDisplayName(l)}
                    </option>
                  ))}
                </select>
              </div>
              {complaint.linkedDisputeId ? (
                <div className="space-y-2 md:col-span-2">
                  <Label>النزاع المرتبط</Label>
                  <Link
                    href={`/disputes/${complaint.linkedDisputeId}`}
                    className="text-sm font-medium text-[var(--color-gold-400)] hover:underline"
                  >
                    {complaint.linkedDisputeTitle ?? complaint.linkedDisputeId}
                  </Link>
                </div>
              ) : null}
            </div>
          </SectionCard>
          </div>

          <div id="lc-content" className="scroll-mt-28">
          <SectionCard title="المحتوى القانوني" description="موضوع الشكوى والملخص والطلب المقدّم">
            <div className="grid gap-4">
              <div className="space-y-2">
                <Label htmlFor="subject">موضوع الشكوى</Label>
                <Input id="subject" value={subject} onChange={(e) => setSubject(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="summary">ملخص الشكوى</Label>
                <Textarea
                  id="summary"
                  rows={4}
                  value={summary}
                  onChange={(e) => setSummary(e.target.value)}
                  placeholder="ملخص موجز لوقائع الشكوى..."
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="legalRequest">الطلب القانوني</Label>
                <Textarea
                  id="legalRequest"
                  rows={4}
                  value={legalRequest}
                  onChange={(e) => setLegalRequest(e.target.value)}
                  placeholder="الطلبات المقدّمة أمام إدارة العمل..."
                />
              </div>
            </div>
          </SectionCard>
          </div>

          <div id="lc-parties" className="scroll-mt-28">
          <SectionCard title="الموكلون والخصوم">
            <div className="space-y-6">
              <div>
                <p className="mb-2 text-sm font-semibold">الموكلون</p>
                <div className="space-y-2">
                  {clientRows.map((row, idx) => (
                    <div key={idx} className="grid gap-2 md:grid-cols-[1fr_160px_auto]">
                      <select
                        className={selectClassName}
                        value={row.clientId}
                        onChange={(e) => {
                          const next = [...clientRows];
                          next[idx] = { ...next[idx], clientId: e.target.value };
                          setClientRows(next);
                        }}
                      >
                        <option value="">اختر موكلًا</option>
                        {clients.map((c) => (
                          <option key={c.id} value={c.id}>
                            {clientDisplayName(c)}
                          </option>
                        ))}
                      </select>
                      <select
                        className={selectClassName}
                        value={row.capacity}
                        onChange={(e) => {
                          const next = [...clientRows];
                          next[idx] = {
                            ...next[idx],
                            capacity: e.target.value as LaborComplaintPartyCapacityDto | "",
                          };
                          setClientRows(next);
                        }}
                      >
                        <option value="">الصفة</option>
                        {LABOR_COMPLAINT_PARTY_CAPACITY_OPTIONS.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() => setClientRows(clientRows.filter((_, i) => i !== idx))}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </div>
                  ))}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setClientRows([...clientRows, { clientId: "", capacity: "" }])}
                  >
                    <Plus className="size-4" />
                    إضافة موكل
                  </Button>
                </div>
              </div>
              <div>
                <p className="mb-2 text-sm font-semibold">الخصوم</p>
                <div className="space-y-2">
                  {opponentRows.map((row, idx) => (
                    <div key={idx} className="grid gap-2 md:grid-cols-[1fr_160px_auto]">
                      <Input
                        value={row.name}
                        onChange={(e) => {
                          const next = [...opponentRows];
                          next[idx] = { ...next[idx], name: e.target.value };
                          setOpponentRows(next);
                        }}
                      />
                      <select
                        className={selectClassName}
                        value={row.capacity}
                        onChange={(e) => {
                          const next = [...opponentRows];
                          next[idx] = {
                            ...next[idx],
                            capacity: e.target.value as LaborComplaintPartyCapacityDto | "",
                          };
                          setOpponentRows(next);
                        }}
                      >
                        <option value="">الصفة</option>
                        {LABOR_COMPLAINT_PARTY_CAPACITY_OPTIONS.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() => setOpponentRows(opponentRows.filter((_, i) => i !== idx))}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </div>
                  ))}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setOpponentRows([...opponentRows, { name: "", capacity: "" }])}
                  >
                    <Plus className="size-4" />
                    إضافة خصم
                  </Button>
                </div>
              </div>
            </div>
          </SectionCard>
          </div>

          <div id="lc-sessions" className="scroll-mt-28">
          <SectionCard title="جلسات إدارة العمل">
            <div className="space-y-3">
              {molSessions.map((s, idx) => (
                <div key={s.id ?? idx} className="grid gap-2 rounded-xl border border-[var(--color-border)] p-3 md:grid-cols-3">
                  <Input
                    type="datetime-local"
                    value={s.sessionDate?.slice(0, 16) ?? ""}
                    onChange={(e) => {
                      const next = [...molSessions];
                      next[idx] = { ...next[idx], sessionDate: new Date(e.target.value).toISOString() };
                      setMolSessions(next);
                    }}
                  />
                  <Input
                    placeholder="المكان"
                    value={s.location ?? ""}
                    onChange={(e) => {
                      const next = [...molSessions];
                      next[idx] = { ...next[idx], location: e.target.value };
                      setMolSessions(next);
                    }}
                  />
                  <Input
                    placeholder="ملاحظات"
                    value={s.notes ?? ""}
                    onChange={(e) => {
                      const next = [...molSessions];
                      next[idx] = { ...next[idx], notes: e.target.value };
                      setMolSessions(next);
                    }}
                  />
                </div>
              ))}
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() =>
                  setMolSessions([
                    ...molSessions,
                    { sessionDate: new Date().toISOString(), location: null, notes: null },
                  ])
                }
              >
                <Plus className="size-4" />
                إضافة جلسة
              </Button>
            </div>
          </SectionCard>
          </div>

          <div id="lc-documents" className="scroll-mt-28">
          <SectionCard title="المرفقات">
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) void handleUpload(file);
                e.target.value = "";
              }}
            />
            <button
              type="button"
              disabled={uploading}
              onClick={() => fileInputRef.current?.click()}
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                const file = e.dataTransfer.files?.[0];
                if (file) void handleUpload(file);
              }}
              className={cn(
                "mb-4 flex w-full flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed px-4 py-8 text-center transition",
                dragOver
                  ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)]/50"
                  : "border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 hover:border-[var(--color-gold-400)]/50",
              )}
            >
              <Upload className="size-8 text-[var(--color-gold-500)]" aria-hidden />
              <span className="text-sm font-medium">
                {uploading ? "جاري الرفع..." : "اسحب الملف هنا أو انقر للرفع"}
              </span>
              <span className="text-xs text-[var(--color-muted-foreground)]">
                {documents.length} ملف — الحد الأقصى 20MB
              </span>
            </button>
            {documents.length === 0 ? (
              <p className="text-sm text-[var(--color-muted-foreground)]">لا توجد مرفقات بعد.</p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)] rounded-xl border border-[var(--color-border)]">
                {documents.map((doc) => (
                  <li key={doc.id} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                    <span className="truncate font-medium">{doc.originalFilename}</span>
                    <a
                      href={laborComplaintsApi.documentDownloadUrl(id, doc.id)}
                      className="shrink-0 text-[var(--color-gold-400)] hover:underline"
                      target="_blank"
                      rel="noreferrer"
                    >
                      تحميل
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </SectionCard>
          </div>
        </div>

        <aside className="space-y-4">
          {complaint.status === "SAVED" && complaint.savedReason ? (
            <SectionCard title="سبب الحفظ">
              <p className="text-sm">{complaint.savedReason}</p>
            </SectionCard>
          ) : null}

          {complaint.status === "REFERRAL_TO_COMMITTEE" ? (
            <SectionCard title="بيانات الإحالة">
              {complaint.linkedCaseNumber ? (
                <p className="text-sm">
                  القضية:{" "}
                  <Link href={`/cases/${complaint.linkedCaseId}`} className="text-[var(--color-gold-400)]">
                    {complaint.linkedCaseNumber}
                  </Link>
                </p>
              ) : null}
              {complaint.referralNotes ? <p className="mt-2 text-sm">{complaint.referralNotes}</p> : null}
            </SectionCard>
          ) : null}

          {complaint.linkedDisputeTeam?.members?.length ? (
            <SectionCard title="فريق النزاع (من النزاع المرتبط)">
              <ul className="space-y-2 text-sm">
                {complaint.linkedDisputeTeam.members.map((m) => (
                  <li key={m.id} className="flex flex-col gap-0.5">
                    <span className="text-[var(--color-muted-foreground)]">
                      {DISPUTE_TEAM_ROLE_AR[m.role] ?? m.role}
                    </span>
                    <span className="font-medium">{m.person?.fullName ?? "—"}</span>
                  </li>
                ))}
              </ul>
            </SectionCard>
          ) : null}

          <SectionCard title="معلومات إضافية">
            <div className="space-y-3 text-sm">
              <div>
                <Label className="text-xs text-[var(--color-muted-foreground)]">الشركاء الخارجيون</Label>
                <Textarea
                  rows={2}
                  className="mt-1"
                  value={externalPartners}
                  onChange={(e) => setExternalPartners(e.target.value)}
                />
              </div>
              <div>
                <Label className="text-xs text-[var(--color-muted-foreground)]">ممثل الخصم</Label>
                <Input className="mt-1" value={opponentRepresentative} onChange={(e) => setOpponentRepresentative(e.target.value)} />
              </div>
              <div>
                <Label className="text-xs text-[var(--color-muted-foreground)]">المكاتب المتعاونة</Label>
                <Input className="mt-1" value={cooperatingOffices} onChange={(e) => setCooperatingOffices(e.target.value)} />
              </div>
            </div>
          </SectionCard>
        </aside>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-[var(--color-border)] bg-[var(--color-surface)]/95 p-3 shadow-[var(--shadow-pop)] backdrop-blur sm:hidden">
        <Button className="w-full" onClick={handleSave} disabled={saving}>
          <Save className="size-4" />
          {saving ? "جاري الحفظ..." : "حفظ التعديلات"}
        </Button>
      </div>

      <LaborComplaintStatusPanel
        open={statusPanelOpen}
        currentStatus={complaint.status}
        onClose={() => setStatusPanelOpen(false)}
        onConfirm={handleStatusConfirm}
        submitting={statusSubmitting}
      />
    </div>
  );
}
