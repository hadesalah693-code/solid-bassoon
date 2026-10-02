"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowRight,
  Download,
  FileText,
  Handshake,
  Plus,
  Trash2,
  Upload,
  Wallet,
  History,
} from "lucide-react";

import { MandateStatusPanel, type MandateSessionFormValues, type MandateStatusFormValues } from "@/components/mandates/MandateStatusPanel";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

import { mandatesApi } from "@/lib/api/mandates";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  MANDATE_DOCUMENT_TYPE_AR,
  MANDATE_DOCUMENT_TYPE_OPTIONS,
  MANDATE_EVENT_TYPE_AR,
  MANDATE_PAYMENT_STATUS_AR,
  MANDATE_PAYMENT_STATUS_TONE,
  MANDATE_PRIORITY_AR,
  MANDATE_PRIORITY_TONE,
  MANDATE_STATUS_AR,
  MANDATE_STATUS_TONE,
} from "@/lib/mandates/labels";
import { cn } from "@/lib/utils/cn";
import { formatDate, formatDateTime } from "@/lib/utils/format-date";
import type {
  MandateDocumentDto,
  MandateDocumentTypeDto,
  MandateEventDto,
  MandatePaymentDto,
  MandateResponseDto,
} from "@/types/mandate";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function partyName(p: { fullName?: string | null } | null | undefined) {
  return p?.fullName?.trim() || "—";
}

export default function MandateDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [mandate, setMandate] = useState<MandateResponseDto | null>(null);
  const [events, setEvents] = useState<MandateEventDto[]>([]);
  const [documents, setDocuments] = useState<MandateDocumentDto[]>([]);
  const [payments, setPayments] = useState<MandatePaymentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [docType, setDocType] = useState<MandateDocumentTypeDto | "">("");
  const [updating, setUpdating] = useState(false);

  const [newPayAmount, setNewPayAmount] = useState("");
  const [newPayDate, setNewPayDate] = useState(new Date().toISOString().slice(0, 10));
  const [newPayMethod, setNewPayMethod] = useState("");
  const [newPayNotes, setNewPayNotes] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [data, evs, docs, pays] = await Promise.all([
        mandatesApi.get(id),
        mandatesApi.listTimeline(id),
        mandatesApi.listDocuments(id),
        mandatesApi.listPayments(id),
      ]);
      setMandate(data);
      setEvents(evs);
      setDocuments(docs);
      setPayments(pays);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل الإنابة");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleStatusChange(values: MandateStatusFormValues) {
    setSubmitting(true);
    try {
      const updated = await mandatesApi.changeStatus(id, {
        status: values.status,
        cancelledReason: values.status === "CANCELLED" ? values.cancelledReason || null : null,
      });
      setMandate(updated);
      setPanelOpen(false);
      notifySuccess("تم تحديث الحالة");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذّر تحديث الحالة");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSessionResult(values: MandateSessionFormValues) {
    setSubmitting(true);
    try {
      const updated = await mandatesApi.recordSessionResult(id, {
        didAttend: values.didAttend,
        sessionResult: values.sessionResult?.trim() || null,
        courtDecision: values.courtDecision?.trim() || null,
        nextSessionDate: values.nextSessionDate || null,
        nextSessionNotes: values.nextSessionNotes?.trim() || null,
      });
      setMandate(updated);
      setPanelOpen(false);
      notifySuccess("تم تسجيل نتيجة الجلسة");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذّر تسجيل نتيجة الجلسة");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleUpload(file: File) {
    setUploading(true);
    try {
      await mandatesApi.uploadDocument(id, file, undefined, docType || undefined);
      notifySuccess("تم رفع الملف");
      const docs = await mandatesApi.listDocuments(id);
      setDocuments(docs);
    } catch (e) {
      notifyApiError(e, "تعذّر رفع الملف");
    } finally {
      setUploading(false);
    }
  }

  async function handleDownload(doc: MandateDocumentDto) {
    try {
      const blob = await mandatesApi.downloadDocument(id, doc.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = doc.originalFilename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      notifyApiError(e, "تعذّر تنزيل الملف");
    }
  }

  async function handleDeleteDocument(doc: MandateDocumentDto) {
    try {
      await mandatesApi.deleteDocument(id, doc.id);
      notifySuccess("تم حذف الملف");
      setDocuments(documents.filter((d) => d.id !== doc.id));
    } catch (e) {
      notifyApiError(e, "تعذّر حذف الملف");
    }
  }

  async function handleAddPayment() {
    if (!newPayAmount.trim()) {
      notifyApiError(new ApiRequestError({ message: "أدخل مبلغ الدفعة", status: 400, code: "BAD_REQUEST", timestamp: "", path: "" }), "أدخل مبلغ الدفعة");
      return;
    }
    setSubmitting(true);
    try {
      await mandatesApi.recordPayment(id, {
        amount: newPayAmount,
        paymentDate: newPayDate,
        method: newPayMethod?.trim() || null,
        notes: newPayNotes?.trim() || null,
      });
      notifySuccess("تم تسجيل الدفعة");
      setNewPayAmount("");
      setNewPayMethod("");
      setNewPayNotes("");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذّر تسجيل الدفعة");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!mandate) return;
    if (!window.confirm("هل تريد حذف هذه الإنابة؟")) return;
    setUpdating(true);
    try {
      await mandatesApi.remove(id);
      notifySuccess("تم حذف الإنابة");
      router.push("/mandates");
    } catch (e) {
      notifyApiError(e, "تعذّر حذف الإنابة");
    } finally {
      setUpdating(false);
    }
  }

  if (loading) return <PageLoading label="جاري تحميل الإنابة..." />;
  if (!mandate) return <ApiErrorAlert message={error ?? "الإنابة غير موجودة"} />;

  const remaining = Number(mandate.fees) - Number(mandate.amountPaid);

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={Handshake}
        eyebrow="الإنابات"
        title={mandate.mandateNumber}
        description={
          <div className="space-y-1">
            <p>
              {mandate.caseNumber} — {mandate.caseTitle}
            </p>
            <p className="text-white/65">
              {[
                mandate.courtName?.trim(),
                mandate.sessionDate ? `الجلسة: ${formatDateTime(mandate.sessionDate)}` : null,
              ]
                .filter(Boolean)
                .join(" · ")}
            </p>
          </div>
        }
        badges={
          <span className="inline-flex items-center gap-2">
            <Badge tone={MANDATE_STATUS_TONE[mandate.status]}>
              {MANDATE_STATUS_AR[mandate.status]}
            </Badge>
            <Badge tone={MANDATE_PRIORITY_TONE[mandate.priority]}>
              {MANDATE_PRIORITY_AR[mandate.priority]}
            </Badge>
          </span>
        }
        actions={
          <>
            <Button variant="outline" asChild className="border-white/25 bg-white/5 text-white hover:bg-white/15 hover:text-white">
              <Link href="/mandates">
                <ArrowRight className="size-4" />
                القائمة
              </Link>
            </Button>
            {mandate.status !== "CANCELLED" ? (
              <Button
                variant="outline"
                onClick={() => setPanelOpen(true)}
                className="border-white/25 bg-white/5 text-white hover:bg-white/15 hover:text-white"
              >
                تحديث
              </Button>
            ) : null}
            {mandate.status !== "COMPLETED" ? (
              <Button
                variant="outline"
                disabled={updating}
                onClick={handleDelete}
                className="gap-2 border-white/25 bg-white/5 text-white hover:bg-red-500/20 hover:text-white"
              >
                <Trash2 className="size-4" />
                حذف
              </Button>
            ) : null}
          </>
        }
      />

      {error ? <ApiErrorAlert message={error} /> : null}

      <nav aria-label="أقسام الإنابة" className="sticky top-0 z-10 flex gap-2 overflow-x-auto border-b border-[var(--color-border)] bg-[var(--color-background)]/95 py-2 backdrop-blur-sm">
        {[
          ["mandate-overview", "نظرة عامة"],
          ["mandate-hearing", "الجلسة"],
          ["mandate-lawyers", "المحامون"],
          ["mandate-payments", "الدفعات"],
          ["mandate-documents", "المستندات"],
          ["mandate-activity", "النشاط"],
        ].map(([target, label]) => (
          <a key={target} href={`#${target}`} className="shrink-0 rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1.5 text-xs font-semibold text-[var(--color-navy-800)] hover:border-[var(--color-gold-400)]">
            {label}
          </a>
        ))}
      </nav>

      <div id="mandate-overview" className="grid scroll-mt-20 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SectionCard title="الرسوم">
          <div className="space-y-1 text-sm">
            <p>
              الإجمالي: <span className="font-semibold ltr-nums">{mandate.fees} {mandate.currency}</span>
            </p>
            <p className="text-[var(--color-muted-foreground)]">
              المدفوع: <span className="ltr-nums">{mandate.amountPaid}</span>
            </p>
            <p className="text-[var(--color-muted-foreground)]">
              المتبقي: <span className="font-semibold ltr-nums text-[var(--color-gold-600)]">{remaining}</span>
            </p>
          </div>
        </SectionCard>
        <SectionCard title="حالة الدفع">
          <Badge tone={MANDATE_PAYMENT_STATUS_TONE[mandate.paymentStatus]}>
            {MANDATE_PAYMENT_STATUS_AR[mandate.paymentStatus]}
          </Badge>
        </SectionCard>
        <SectionCard title="القضية">
          <Link
            href={`/cases/${mandate.caseId}`}
            className="text-sm font-medium text-[var(--color-gold-600)] hover:underline"
          >
            {mandate.caseNumber}
          </Link>
        </SectionCard>
        <SectionCard title="العملية">
          <div className="space-y-1 text-sm text-[var(--color-muted-foreground)]">
            <p>أُنشئت: {formatDate(mandate.createdAt)}</p>
            {mandate.closedAt ? <p>أُغلقت: {formatDate(mandate.closedAt)}</p> : null}
          </div>
        </SectionCard>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-6">
          <section id="mandate-lawyers" className="scroll-mt-20">
          <SectionCard title="تفاصيل الإنابة">
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <Label className="text-xs text-[var(--color-muted-foreground)]">العميل</Label>
                <p className="mt-1 font-medium">{partyName(mandate.client)}</p>
              </div>
              <div>
                <Label className="text-xs text-[var(--color-muted-foreground)]">المحامي الأصلي</Label>
                <p className="mt-1 font-medium">{partyName(mandate.originalLawyer)}</p>
              </div>
              <div>
                <Label className="text-xs text-[var(--color-muted-foreground)]">المحامي المُنتدب</Label>
                <p className="mt-1 font-medium">{partyName(mandate.substitutedLawyer)}</p>
              </div>
              <div>
                <Label className="text-xs text-[var(--color-muted-foreground)]">الدائرة / القاعة</Label>
                <p className="mt-1 font-medium">
                  {[mandate.courtCircuit, mandate.courtroom].filter(Boolean).join(" · ") || "—"}
                </p>
              </div>
            </div>
            {mandate.reason ? (
              <div className="mt-4">
                <Label className="text-xs text-[var(--color-muted-foreground)]">سبب الإنابة</Label>
                <p className="mt-1 whitespace-pre-wrap text-sm">{mandate.reason}</p>
              </div>
            ) : null}
            {mandate.instructions ? (
              <div className="mt-4">
                <Label className="text-xs text-[var(--color-muted-foreground)]">تعليمات المحامي المُنتدب</Label>
                <p className="mt-1 whitespace-pre-wrap text-sm">{mandate.instructions}</p>
              </div>
            ) : null}
            {mandate.notes ? (
              <div className="mt-4">
                <Label className="text-xs text-[var(--color-muted-foreground)]">ملاحظات</Label>
                <p className="mt-1 whitespace-pre-wrap text-sm">{mandate.notes}</p>
              </div>
            ) : null}
            {mandate.cancelledReason ? (
              <div className="mt-4 rounded-xl border border-[var(--color-danger)]/30 bg-[var(--color-danger-bg)]/40 p-3">
                <Label className="text-xs text-[var(--color-danger)]">سبب الإلغاء</Label>
                <p className="mt-1 whitespace-pre-wrap text-sm">{mandate.cancelledReason}</p>
              </div>
            ) : null}
          </SectionCard>
          </section>

          <section id="mandate-payments" className="scroll-mt-20">
          <SectionCard
            title="الدفعات"
            description="الدفعات المسجّلة على الإنابة"
            action={<Wallet className="size-5 text-[var(--color-muted-foreground)]" aria-hidden />}
          >
            <div className="mb-4 grid gap-3 md:grid-cols-2">
              <div className="space-y-2 md:col-span-2">
                <Label htmlFor="pay-amount">مبلغ الدفعة *</Label>
                <Input
                  id="pay-amount"
                  type="number"
                  min="0"
                  step="0.01"
                  value={newPayAmount}
                  onChange={(e) => setNewPayAmount(e.target.value)}
                  placeholder="0.00"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="pay-date">تاريخ الدفع</Label>
                <Input
                  id="pay-date"
                  type="date"
                  value={newPayDate}
                  onChange={(e) => setNewPayDate(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="pay-method">طريقة الدفع</Label>
                <Input
                  id="pay-method"
                  value={newPayMethod}
                  onChange={(e) => setNewPayMethod(e.target.value)}
                  placeholder="نقدي / تحويل / شيك"
                />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Button type="button" onClick={handleAddPayment} disabled={submitting}>
                  <Plus className="size-4" />
                  تسجيل دفعة
                </Button>
              </div>
            </div>

            {payments.length === 0 ? (
              <p className="text-sm text-[var(--color-muted-foreground)]">لا توجد دفعات بعد.</p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)] rounded-xl border border-[var(--color-border)]">
                {payments.map((p) => (
                  <li key={p.id} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                    <div className="min-w-0">
                      <p className="font-semibold ltr-nums text-[var(--color-gold-600)]">
                        {p.amount} {mandate.currency}
                      </p>
                      <p className="text-xs text-[var(--color-muted-foreground)]">
                        {formatDate(p.paymentDate)}
                        {p.method ? ` · ${p.method}` : ""}
                      </p>
                    </div>
                    {p.recordedByEmail ? (
                      <span className="truncate text-xs text-[var(--color-muted-foreground)]">
                        {p.recordedByEmail}
                      </span>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </SectionCard>
          </section>

          <section id="mandate-activity" className="scroll-mt-20">
          <SectionCard title="الإطار الزمني" description="سجل أحداث الإنابة">
            <History className="mb-2 size-5 text-[var(--color-muted-foreground)]" aria-hidden />
            {events.length === 0 ? (
              <p className="text-sm text-[var(--color-muted-foreground)]">لا توجد أحداث بعد.</p>
            ) : (
              <ol className="relative space-y-4 border-s border-[var(--color-border)] ps-5">
                {events.map((ev) => (
                  <li key={ev.id} className="relative">
                    <span
                      className={cn(
                        "absolute -start-[1.42rem] top-1 size-3 rounded-full ring-4 ring-[var(--color-surface)]",
                        ev.eventType === "CANCELLED"
                          ? "bg-[var(--color-danger)]"
                          : ev.eventType === "CREATED" || ev.eventType === "CONFIRMED"
                            ? "bg-[var(--color-gold-400)]"
                            : "bg-[var(--color-navy-400)]",
                      )}
                      aria-hidden
                    />
                    <div className="text-sm">
                      <p className="font-medium">{MANDATE_EVENT_TYPE_AR[ev.eventType]}</p>
                      {ev.note ? <p className="mt-0.5 text-[var(--color-muted-foreground)]">{ev.note}</p> : null}
                      <p className="mt-0.5 text-xs text-[var(--color-muted-foreground)]">
                        {formatDateTime(ev.createdAt)}
                        {ev.actorEmail ? ` · ${ev.actorEmail}` : ""}
                      </p>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </SectionCard>
          </section>

          <section id="mandate-documents" className="scroll-mt-20">
          <SectionCard title="المرفقات">
            <div className="mb-3 flex flex-wrap items-center gap-2">
              <select
                className={selectClassName}
                style={{ width: "auto", minWidth: "10rem" }}
                value={docType}
                onChange={(e) => setDocType(e.target.value as MandateDocumentTypeDto | "")}
                aria-label="نوع المستند"
              >
                <option value="">كل الأنواع</option>
                {MANDATE_DOCUMENT_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>
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
                {documents.length} ملف — الحد الأقصى 25MB
              </span>
            </button>
            {documents.length === 0 ? (
              <p className="text-sm text-[var(--color-muted-foreground)]">لا توجد مرفقات بعد.</p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)] rounded-xl border border-[var(--color-border)]">
                {documents.map((doc) => (
                  <li key={doc.id} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                    <div className="flex min-w-0 items-center gap-3">
                      <FileText className="size-5 shrink-0 text-[var(--color-muted-foreground)]" aria-hidden />
                      <div className="min-w-0">
                        <p className="truncate font-medium">{doc.originalFilename}</p>
                        <p className="text-xs text-[var(--color-muted-foreground)]">
                          {MANDATE_DOCUMENT_TYPE_AR[doc.documentType]}
                          {doc.description ? ` — ${doc.description}` : ""}
                        </p>
                      </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() => void handleDownload(doc)}
                        title="تحميل"
                      >
                        <Download className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="text-[var(--color-danger)]"
                        onClick={() => void handleDeleteDocument(doc)}
                        title="حذف"
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </SectionCard>
          </section>
        </div>

        <aside className="space-y-4">
          <section id="mandate-hearing" className="scroll-mt-20">
          {mandate.sessionResult !== null ? (
            <SectionCard title="نتيجة الجلسة">
              <div className="space-y-3 text-sm">
                {mandate.didAttend !== null ? (
                  <p>
                    الحضور:{" "}
                    <span className="font-medium">{mandate.didAttend ? "تم الحضور" : "لم يحضر"}</span>
                  </p>
                ) : null}
                {mandate.sessionResult ? <p className="whitespace-pre-wrap">{mandate.sessionResult}</p> : null}
                {mandate.courtDecision ? (
                  <div>
                    <Label className="text-xs text-[var(--color-muted-foreground)]">قرار المحكمة</Label>
                    <p className="mt-1 whitespace-pre-wrap">{mandate.courtDecision}</p>
                  </div>
                ) : null}
                {mandate.nextSessionDate ? (
                  <p className="text-[var(--color-muted-foreground)]">
                    الجلسة القادمة: {formatDateTime(mandate.nextSessionDate)}
                  </p>
                ) : null}
              </div>
            </SectionCard>
          ) : (
            <SectionCard title="نتيجة الجلسة">
              <p className="text-sm text-[var(--color-muted-foreground)]">لم تُسجَّل نتيجة الجلسة بعد.</p>
              <Button type="button" className="mt-3 w-full" onClick={() => setPanelOpen(true)}>
                تسجيل النتيجة
              </Button>
            </SectionCard>
          )}
          </section>

          <SectionCard title="الدفع">
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-[var(--color-muted-foreground)]">الرسوم</span>
                <span className="font-medium ltr-nums">{mandate.fees}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[var(--color-muted-foreground)]">المدفوع</span>
                <span className="font-medium ltr-nums">{mandate.amountPaid}</span>
              </div>
              <div className="flex justify-between border-t border-[var(--color-border)] pt-2">
                <span className="text-[var(--color-muted-foreground)]">المتبقي</span>
                <span className="font-semibold ltr-nums text-[var(--color-gold-600)]">{remaining}</span>
              </div>
            </div>
          </SectionCard>
        </aside>
      </div>

      <MandateStatusPanel
        open={panelOpen}
        currentStatus={mandate.status}
        cancelled={mandate.status === "CANCELLED"}
        onClose={() => setPanelOpen(false)}
        onStatusChange={handleStatusChange}
        onSessionResult={handleSessionResult}
        submitting={submitting}
      />
    </div>
  );
}
