"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowRight, FileSignature, PenLine, ScanLine } from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

import { DelegationAgentsPicker } from "@/components/delegations/DelegationAgentsPicker";
import { DelegationOcrIntakePanel } from "@/components/delegations/DelegationOcrIntakePanel";
import { DelegationOcrReviewCard } from "@/components/delegations/DelegationOcrReviewCard";
import { DelegationPartnersPicker } from "@/components/delegations/DelegationPartnersPicker";
import { ReminderRecipientsPicker } from "@/components/delegations/ReminderRecipientsPicker";
import { StaffMentionTextarea } from "@/components/delegations/StaffMentionTextarea";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { delegationAttorneysApi } from "@/lib/api/delegation-attorneys";
import { delegationDocumentsApi } from "@/lib/api/delegation-documents";
import { delegationIntakeApi } from "@/lib/api/delegation-intake";
import { delegationsApi } from "@/lib/api/delegations";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  DELEGATION_TYPE_AR,
  ISSUING_COUNTRIES,
  REMINDER_OFFSET_OPTIONS,
} from "@/lib/delegations/labels";
import { buildDelegationTitle } from "@/lib/delegations/build-title";
import { STAFF_ROLES, clientDisplayName, toMentionStaff } from "@/lib/delegations/staff";
import { cn } from "@/lib/utils/cn";
import type { DelegationIntakeDto } from "@/lib/api/delegation-intake";
import type { DelegationTypeDto } from "@/types/delegation";
import type { ClientPerson } from "@/types/person";
import type { OcrRecordDto } from "@/types/ocr";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const schema = z.object({
  clientId: z.string().min(1, "اختر الموكل"),
  delegationType: z.string().min(1),
  issuingCountry: z.string().length(2),
  issuedAt: z.string().min(1, "تاريخ الإصدار مطلوب"),
  expiresAt: z.string().optional(),
  notaryNumber: z.string().optional(),
  notes: z.string().optional(),
}).refine((d) => !d.issuedAt || !d.expiresAt || d.expiresAt >= d.issuedAt, {
  message: "تاريخ الانتهاء يجب أن يكون بعد الإصدار",
  path: ["expiresAt"],
});

type FormValues = z.infer<typeof schema>;
type IntakeMode = "manual" | "ocr";

function clientLabel(p: ClientPerson) {
  return clientDisplayName(p);
}

export default function NewDelegationPage() {
  return (
    <Suspense fallback={null}>
      <NewDelegationPageInner />
    </Suspense>
  );
}

function NewDelegationPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const presetClientId = searchParams.get("clientId") ?? "";
  const [mode, setMode] = useState<IntakeMode>("manual");
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [attorneys, setAttorneys] = useState<{ id: string; name: string }[]>([]);
  const [pickError, setPickError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [reminderOffsets, setReminderOffsets] = useState<number[]>([90, 30]);
  const [reminderRecipients, setReminderRecipients] = useState<string[]>([]);
  const [partnerIds, setPartnerIds] = useState<string[]>([]);
  const [agentIds, setAgentIds] = useState<string[]>([]);
  const [ocrRecordId, setOcrRecordId] = useState<string | null>(null);
  const [ocrIntake, setOcrIntake] = useState<DelegationIntakeDto | null>(null);
  const [ocrConfirmed, setOcrConfirmed] = useState(false);
  const [ocrPrefillNote, setOcrPrefillNote] = useState<string | null>(null);
  const [reminderStaff, setReminderStaff] = useState<ClientPerson[]>([]);
  const [notes, setNotes] = useState("");
  const agentsSeededRef = useRef(false);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      clientId: "",
      delegationType: "JUDICIAL",
      issuingCountry: "QA",
      issuedAt: new Date().toISOString().slice(0, 10),
      expiresAt: "",
      notaryNumber: "",
      notes: "",
    },
  });

  useEffect(() => {
    if (presetClientId) {
      form.setValue("clientId", presetClientId);
    }
  }, [presetClientId, form]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setPickError(null);
      try {
        const [cRes, attorneyRows, autoAgent, ...staffPages] = await Promise.all([
          personsApi.list({ role: "CLIENT", size: 500 }),
          delegationAttorneysApi.list(true),
          delegationAttorneysApi.autoAgent().catch(() => null),
          ...STAFF_ROLES.map((r) => personsApi.list({ role: r, size: 200 })),
        ]);
        if (!cancelled) {
          setClients(cRes.content);
          const mergedStaff = staffPages.flatMap((p) => p.content);
          const seenStaff = new Set<string>();
          setReminderStaff(mergedStaff.filter((p) => !seenStaff.has(p.id) && seenStaff.add(p.id)));
          setAttorneys(attorneyRows.map((a) => ({
            id: a.person.id,
            name: a.person.fullName?.trim() || a.person.id,
          })));
          if (!agentsSeededRef.current) {
            if (autoAgent) {
              setAgentIds([autoAgent.id]);
              agentsSeededRef.current = true;
            } else if (attorneyRows.length === 1) {
              setAgentIds([attorneyRows[0].person.id]);
              agentsSeededRef.current = true;
            }
          }
        }
      } catch (e) {
        if (!cancelled) {
          setPickError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل القوائم");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  function applyIntake(intake: DelegationIntakeDto, record: OcrRecordDto) {
    void record;
    setOcrRecordId(intake.ocrRecordId);
    setOcrIntake(intake);
    setOcrConfirmed(false);
    const noteLines: string[] = [];
    if (intake.suggestedClient?.id) {
      form.setValue("clientId", intake.suggestedClient.id);
    } else if (intake.suggestedClientName) {
      noteLines.push(`موكل مقترح من OCR: ${intake.suggestedClientName}`);
    }
    if (intake.agentMatch.matched && intake.agentMatch.person) {
      setAgentIds([intake.agentMatch.person.id]);
    } else if (intake.suggestedAgentName) {
      noteLines.push(`وكيل مقترح من OCR: ${intake.suggestedAgentName}`);
    }
    if (intake.notaryNumber) form.setValue("notaryNumber", intake.notaryNumber);
    if (intake.issuedAt) form.setValue("issuedAt", intake.issuedAt);
    if (intake.expiresAt) form.setValue("expiresAt", intake.expiresAt);
    if (intake.delegationType) form.setValue("delegationType", intake.delegationType);
    if (intake.issuingCountry) form.setValue("issuingCountry", intake.issuingCountry);
    if (noteLines.length) {
      const existing = form.getValues("notes")?.trim();
      const merged = [existing, ...noteLines].filter(Boolean).join("\n");
      form.setValue("notes", merged);
      setNotes(merged);
    }
    setOcrPrefillNote("تم استخراج البيانات — راجع بطاقة التأكيد ثم الحقول أدناه");
  }

  function toggleOffset(days: number) {
    setReminderOffsets((prev) =>
      prev.includes(days) ? prev.filter((d) => d !== days) : [...prev, days].sort((a, b) => b - a),
    );
  }

  async function onSubmit(values: FormValues) {
    setSubmitting(true);
    setSubmitError(null);
    try {
      const created = await delegationsApi.create({
        clientId: values.clientId,
        agentIds: agentIds.length > 0 ? agentIds : undefined,
        delegationType: values.delegationType as DelegationTypeDto,
        issuingCountry: values.issuingCountry,
        issuedAt: values.issuedAt,
        expiresAt: values.expiresAt?.trim() || null,
        notaryNumber: values.notaryNumber?.trim() || null,
        partnerIds: partnerIds.length > 0 ? partnerIds : undefined,
        notes: values.notes || notes || null,
        reminderOffsets,
        reminderRecipientIds: reminderRecipients,
      });

      if (ocrRecordId) {
        await delegationDocumentsApi.attachFromOcr(created.id, ocrRecordId);
        await delegationIntakeApi.linkOcr(created.id, ocrRecordId);
      }

      notifySuccess("تم إنشاء التوكيل");
      router.push(`/delegations/${created.id}`);
    } catch (e) {
      const msg = e instanceof ApiRequestError ? e.message : "تعذّر إنشاء التوكيل";
      setSubmitError(msg);
      notifyApiError(e, "تعذّر إنشاء التوكيل");
    } finally {
      setSubmitting(false);
    }
  }

  const agentOptions = attorneys.length > 0 ? attorneys : [];
  const watchedClientId = form.watch("clientId");
  const watchedNotary = form.watch("notaryNumber");
  const selectedClient = clients.find((c) => c.id === watchedClientId);
  const titlePreview = buildDelegationTitle(
    selectedClient ? clientLabel(selectedClient) : "—",
    watchedNotary,
  );
  const canSave = mode === "manual" || (ocrRecordId && ocrConfirmed);

  return (
    <div className="space-y-6 pb-10">
      <DisputeModuleHero
        eyebrow="توكيل جديد"
        title="تسجيل توكيل"
        description={
          mode === "ocr"
            ? "ارفع مستند التوكيل لاستخراج البيانات تلقائياً، ثم راجعها واحفظ."
            : "يُولَّد رقم التوكيل والعنوان تلقائياً بعد الحفظ."
        }
        icon={FileSignature}
        actions={
          <Button asChild variant="outline" className="border-white/25 bg-white/5 text-white hover:bg-white/10">
            <Link href="/delegations">
              <ArrowRight className="ms-1 size-4" aria-hidden />
              العودة للقائمة
            </Link>
          </Button>
        }
      />

      <div className="inline-flex rounded-xl bg-[var(--color-surface-muted)] p-1 ring-1 ring-[var(--color-border)]/60">
        <button
          type="button"
          className={cn(
            "inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm transition-colors",
            mode === "manual"
              ? "bg-[var(--color-surface)] shadow-[var(--shadow-soft)] text-[var(--color-foreground)]"
              : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
          )}
          onClick={() => setMode("manual")}
        >
          <PenLine className="size-4" aria-hidden />
          إدخال يدوي
        </button>
        <button
          type="button"
          className={cn(
            "inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm transition-colors",
            mode === "ocr"
              ? "bg-[var(--color-surface)] shadow-[var(--shadow-soft)] text-[var(--color-foreground)]"
              : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
          )}
          onClick={() => setMode("ocr")}
        >
          <ScanLine className="size-4" aria-hidden />
          مسح / OCR
        </button>
      </div>

      {pickError ? <ApiErrorAlert message={pickError} /> : null}
      {submitError ? <ApiErrorAlert message={submitError} /> : null}
      {ocrPrefillNote ? (
        <p className="rounded-xl border border-[var(--color-gold-400)]/40 bg-[var(--color-gold-50)]/50 px-4 py-3 text-sm text-[var(--color-foreground)]">
          {ocrPrefillNote}
        </p>
      ) : null}

      {mode === "ocr" ? (
        <SectionCard title="مسح مستند التوكيل">
          <DelegationOcrIntakePanel disabled={submitting} onParsed={applyIntake} />
        </SectionCard>
      ) : null}

      {mode === "ocr" && ocrIntake ? (
        <DelegationOcrReviewCard
          intake={ocrIntake}
          clientName={selectedClient ? clientLabel(selectedClient) : "—"}
          confirmed={ocrConfirmed}
          onConfirm={() => setOcrConfirmed(true)}
        />
      ) : null}

      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <SectionCard title="المعلومات الأساسية">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2 md:col-span-2">
              <Label>عنوان التوكيل (متوقع)</Label>
              <Input value={titlePreview} readOnly disabled className="bg-[var(--color-surface-muted)]/50" />
              <p className="text-xs text-[var(--color-muted-foreground)]">يُولَّد تلقائياً: اسم الموكل / رقم التوثيق</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="clientId">الموكل *</Label>
              <select id="clientId" className={selectClassName} {...form.register("clientId")}>
                <option value="">— اختر —</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>{clientLabel(c)}</option>
                ))}
              </select>
              {form.formState.errors.clientId ?
                <p className="text-xs text-[var(--color-danger)]">{form.formState.errors.clientId.message}</p>
              : null}
            </div>
            <div className="space-y-2 md:col-span-2">
              <DelegationPartnersPicker
                clients={clients}
                excludeClientId={form.watch("clientId")}
                selectedIds={partnerIds}
                onChange={setPartnerIds}
              />
            </div>
            <div className="space-y-2 md:col-span-2">
              <DelegationAgentsPicker
                attorneys={agentOptions}
                selectedIds={agentIds}
                onChange={setAgentIds}
              />
              {agentOptions.length === 0 ? (
                <p className="text-xs text-[var(--color-muted-foreground)]">
                  <Link href="/delegations/settings" className="text-[var(--color-gold-600)] underline-offset-2 hover:underline">
                    إعدادات التوكيلات
                  </Link>
                </p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="notaryNumber">رقم التوثيق</Label>
              <Input id="notaryNumber" placeholder="2026/123456" {...form.register("notaryNumber")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="delegationType">نوع التوكيل *</Label>
              <select id="delegationType" className={selectClassName} {...form.register("delegationType")}>
                {(Object.entries(DELEGATION_TYPE_AR) as [DelegationTypeDto, string][]).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="issuingCountry">بلد الإصدار *</Label>
              <select id="issuingCountry" className={selectClassName} {...form.register("issuingCountry")}>
                {ISSUING_COUNTRIES.map((c) => (
                  <option key={c.code} value={c.code}>{c.flag} {c.label}</option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="issuedAt">تاريخ الإصدار *</Label>
              <Input id="issuedAt" type="date" {...form.register("issuedAt")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="expiresAt">تاريخ الانتهاء (اختياري)</Label>
              <Input id="expiresAt" type="date" {...form.register("expiresAt")} />
              {form.formState.errors.expiresAt ?
                <p className="text-xs text-[var(--color-danger)]">{form.formState.errors.expiresAt.message}</p>
              : null}
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="notes">ملاحظات</Label>
              <StaffMentionTextarea
                id="notes"
                value={notes || form.watch("notes") || ""}
                onChange={(v) => {
                  setNotes(v);
                  form.setValue("notes", v);
                }}
                staff={toMentionStaff(reminderStaff)}
              />
            </div>
          </div>
        </SectionCard>

        <SectionCard title="التذكيرات">
          <p className="mb-3 text-sm text-[var(--color-muted-foreground)]">
            اختر مواعيد التذكير قبل انتهاء التوكيل والموظفين المستلمين.
          </p>
          <div className="mb-4 flex flex-wrap gap-2">
            {REMINDER_OFFSET_OPTIONS.map((days) => (
              <label
                key={days}
                className={cn(
                  "flex cursor-pointer items-center gap-2 rounded-xl border px-3 py-2 text-sm transition-colors",
                  reminderOffsets.includes(days) ?
                    "border-[var(--color-gold-400)] bg-[var(--color-gold-50)]"
                  : "border-[var(--color-border)]",
                )}
              >
                <input
                  type="checkbox"
                  className="accent-[var(--color-gold-500)]"
                  checked={reminderOffsets.includes(days)}
                  onChange={() => toggleOffset(days)}
                />
                {days} يوم
              </label>
            ))}
          </div>
          <ReminderRecipientsPicker
            staff={toMentionStaff(reminderStaff)}
            selectedIds={reminderRecipients}
            onChange={setReminderRecipients}
          />
        </SectionCard>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" asChild>
            <Link href="/delegations">إلغاء</Link>
          </Button>
          <Button type="submit" disabled={submitting || !canSave}>
            {submitting ? "جاري الحفظ..." : "حفظ التوكيل"}
          </Button>
        </div>
      </form>
    </div>
  );
}
