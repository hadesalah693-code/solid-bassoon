"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowRight, Handshake } from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

import { mandatesApi } from "@/lib/api/mandates";
import { casesApi } from "@/lib/api/cases";
import { personsApi } from "@/lib/api/persons";
import { hearingsApi } from "@/lib/api/hearings";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { MANDATE_PRIORITY_OPTIONS, CURRENCY_OPTIONS } from "@/lib/mandates/labels";
import { STAFF_ROLES, clientDisplayName } from "@/lib/delegations/staff";
import { cn } from "@/lib/utils/cn";
import { formatDate } from "@/lib/utils/format-date";
import type { ClientPerson } from "@/types/person";
import type { CaseResponseDto, HearingResponseDto } from "@/types/api";
import type { MandatePriorityDto } from "@/types/mandate";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const schema = z.object({
  caseId: z.string().min(1, "اختر القضية"),
  hearingId: z.string().optional(),
  clientId: z.string().min(1, "اختر العميل"),
  originalLawyerId: z.string().min(1, "اختر المحامي الأصلي"),
  substitutedLawyerId: z.string().min(1, "اختر المحامي المُنتدب"),
  priority: z.string().min(1, "اختر الأولوية"),
  sessionDate: z.string().optional(),
  courtName: z.string().optional(),
  courtCircuit: z.string().optional(),
  courtroom: z.string().optional(),
  reason: z.string().optional(),
  notes: z.string().optional(),
  instructions: z.string().optional(),
  fees: z.string().optional(),
  currency: z.string().optional(),
  amountPaid: z.string().optional(),
}).refine((d) => d.substitutedLawyerId !== d.originalLawyerId, {
  message: "المحامي المُنتدب يجب أن يختلف عن المحامي الأصلي",
  path: ["substitutedLawyerId"],
});

type FormValues = z.infer<typeof schema>;

export default function NewMandatePage() {
  const router = useRouter();
  const [cases, setCases] = useState<CaseResponseDto[]>([]);
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [lawyers, setLawyers] = useState<ClientPerson[]>([]);
  const [hearings, setHearings] = useState<HearingResponseDto[]>([]);
  const [pickError, setPickError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      caseId: "",
      hearingId: "",
      clientId: "",
      originalLawyerId: "",
      substitutedLawyerId: "",
      priority: "MEDIUM",
      sessionDate: "",
      courtName: "",
      courtCircuit: "",
      courtroom: "",
      reason: "",
      notes: "",
      instructions: "",
      fees: "",
      currency: "SAR",
      amountPaid: "",
    },
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setPickError(null);
      try {
        const [casesRes, clientsRes, ...staffPages] = await Promise.all([
          casesApi.list({ size: 200, includeArchived: false }),
          personsApi.list({ role: "CLIENT", size: 500 }),
          ...STAFF_ROLES.map((r) => personsApi.list({ role: r, size: 200 })),
        ]);
        if (!cancelled) {
          setCases(casesRes.content);
          setClients(clientsRes.content);
          const merged = staffPages.flatMap((p) => p.content);
          const seen = new Set<string>();
          setLawyers(merged.filter((p) => !seen.has(p.id) && seen.add(p.id)));
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

  const watchedCaseId = form.watch("caseId");
  const selectedCase = useMemo(
    () => cases.find((c) => c.id === watchedCaseId) ?? null,
    [cases, watchedCaseId],
  );

  useEffect(() => {
    if (!watchedCaseId) {
      setHearings([]);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res = await hearingsApi.listByCase(watchedCaseId, { size: 100 });
        if (!cancelled) setHearings(res.content);
      } catch {
        if (!cancelled) setHearings([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [watchedCaseId]);

  function handleCaseChange(caseId: string) {
    form.setValue("caseId", caseId);
    form.setValue("hearingId", "");
    const c = cases.find((x) => x.id === caseId);
    if (c) {
      if (c.client?.id) form.setValue("clientId", c.client.id);
      if (c.courtName) {
        const current = form.getValues("courtName") ?? "";
        if (!current) form.setValue("courtName", c.courtName);
      }
    }
  }

  async function onSubmit(values: FormValues) {
    setSubmitting(true);
    setSubmitError(null);
    try {
      const created = await mandatesApi.create({
        caseId: values.caseId,
        hearingId: values.hearingId || null,
        clientId: values.clientId,
        originalLawyerId: values.originalLawyerId,
        substitutedLawyerId: values.substitutedLawyerId,
        priority: values.priority as MandatePriorityDto,
        sessionDate: values.sessionDate || null,
        courtName: values.courtName?.trim() || null,
        courtCircuit: values.courtCircuit?.trim() || null,
        courtroom: values.courtroom?.trim() || null,
        reason: values.reason?.trim() || null,
        notes: values.notes?.trim() || null,
        instructions: values.instructions?.trim() || null,
        fees: values.fees?.trim() || null,
        currency: values.currency?.trim() || null,
        amountPaid: values.amountPaid?.trim() || null,
      });
      notifySuccess("تم إنشاء الإنابة");
      router.push(`/mandates/${created.id}`);
    } catch (e) {
      const msg = e instanceof ApiRequestError ? e.message : "تعذّر إنشاء الإنابة";
      setSubmitError(msg);
      notifyApiError(e, "تعذّر إنشاء الإنابة");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        eyebrow="إنابة جديدة"
        title="تسجيل إنابة محامٍ"
        description="نقل التمثيل في قضية من محامٍ أصلي إلى محامٍ مُنتدب مع بيانات الجلسة والرسوم."
        icon={Handshake}
        actions={
          <Button asChild variant="outline" className="border-white/25 bg-white/5 text-white hover:bg-white/10">
            <Link href="/mandates">
              <ArrowRight className="ms-1 size-4" aria-hidden />
              العودة للقائمة
            </Link>
          </Button>
        }
      />

      {pickError ? <ApiErrorAlert message={pickError} /> : null}
      {submitError ? <ApiErrorAlert message={submitError} /> : null}

      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <SectionCard title="القضية والأطراف">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="caseId">القضية *</Label>
              <select
                id="caseId"
                className={selectClassName}
                value={form.watch("caseId")}
                onChange={(e) => handleCaseChange(e.target.value)}
              >
                <option value="">— اختر القضية —</option>
                {cases.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.caseNumber} — {c.title}
                  </option>
                ))}
              </select>
              {form.formState.errors.caseId ?
                <p className="text-xs text-[var(--color-danger)]">{form.formState.errors.caseId.message}</p>
              : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="hearingId">الجلسة (اختياري)</Label>
              <select
                id="hearingId"
                className={selectClassName}
                value={form.watch("hearingId") ?? ""}
                onChange={(e) => form.setValue("hearingId", e.target.value)}
              >
                <option value="">بدون جلسة محددة</option>
                {hearings.map((h) => (
                  <option key={h.id} value={h.id}>
                    {formatDate(h.hearingDate)} — {h.courtName}
                  </option>
                ))}
              </select>
              {!watchedCaseId ? (
                <p className="text-xs text-[var(--color-muted-foreground)]">اختر القضية أولاً لعرض جلساتها</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="clientId">العميل *</Label>
              <select
                id="clientId"
                className={cn(selectClassName, selectedCase && "bg-[var(--color-surface-muted)]/60")}
                value={form.watch("clientId")}
                onChange={(e) => form.setValue("clientId", e.target.value)}
                disabled={!!selectedCase}
              >
                <option value="">— اختر العميل —</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>
                    {clientDisplayName(c)}
                  </option>
                ))}
              </select>
              {selectedCase ? (
                <p className="text-xs text-[var(--color-muted-foreground)]">يُحدَّد العميل تلقائياً من القضية</p>
              ) : null}
              {form.formState.errors.clientId ?
                <p className="text-xs text-[var(--color-danger)]">{form.formState.errors.clientId.message}</p>
              : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="originalLawyerId">المحامي الأصلي *</Label>
              <select
                id="originalLawyerId"
                className={selectClassName}
                value={form.watch("originalLawyerId")}
                onChange={(e) => form.setValue("originalLawyerId", e.target.value)}
              >
                <option value="">— اختر —</option>
                {lawyers.map((l) => (
                  <option key={l.id} value={l.id}>
                    {clientDisplayName(l)}
                  </option>
                ))}
              </select>
              {form.formState.errors.originalLawyerId ?
                <p className="text-xs text-[var(--color-danger)]">{form.formState.errors.originalLawyerId.message}</p>
              : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="substitutedLawyerId">المحامي المُنتدب *</Label>
              <select
                id="substitutedLawyerId"
                className={selectClassName}
                value={form.watch("substitutedLawyerId")}
                onChange={(e) => form.setValue("substitutedLawyerId", e.target.value)}
              >
                <option value="">— اختر —</option>
                {lawyers.map((l) => (
                  <option key={l.id} value={l.id}>
                    {clientDisplayName(l)}
                  </option>
                ))}
              </select>
              {form.formState.errors.substitutedLawyerId ?
                <p className="text-xs text-[var(--color-danger)]">{form.formState.errors.substitutedLawyerId.message}</p>
              : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="priority">الأولوية *</Label>
              <select
                id="priority"
                className={selectClassName}
                value={form.watch("priority")}
                onChange={(e) => form.setValue("priority", e.target.value)}
              >
                {MANDATE_PRIORITY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </SectionCard>

        <SectionCard title="بيانات الجلسة والمحكمة">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="sessionDate">موعد الجلسة</Label>
              <Input
                id="sessionDate"
                type="datetime-local"
                value={form.watch("sessionDate") ?? ""}
                onChange={(e) => form.setValue("sessionDate", e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="courtName">المحكمة</Label>
              <Input
                id="courtName"
                value={form.watch("courtName") ?? ""}
                onChange={(e) => form.setValue("courtName", e.target.value)}
                placeholder={selectedCase?.courtName ?? "اسم المحكمة"}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="courtCircuit">الدائرة</Label>
              <Input
                id="courtCircuit"
                value={form.watch("courtCircuit") ?? ""}
                onChange={(e) => form.setValue("courtCircuit", e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="courtroom">رقم القاعة</Label>
              <Input
                id="courtroom"
                value={form.watch("courtroom") ?? ""}
                onChange={(e) => form.setValue("courtroom", e.target.value)}
              />
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="reason">سبب الإنابة</Label>
              <Textarea
                id="reason"
                rows={3}
                value={form.watch("reason") ?? ""}
                onChange={(e) => form.setValue("reason", e.target.value)}
                placeholder="سبب نقل التمثيل إلى المحامي المُنتدب..."
              />
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="instructions">تعليمات للمحامي المُنتدب</Label>
              <Textarea
                id="instructions"
                rows={3}
                value={form.watch("instructions") ?? ""}
                onChange={(e) => form.setValue("instructions", e.target.value)}
              />
            </div>
          </div>
        </SectionCard>

        <SectionCard title="الرسوم والدفع">
          <div className="grid gap-4 md:grid-cols-3">
            <div className="space-y-2">
              <Label htmlFor="fees">إجمالي الرسوم</Label>
              <Input
                id="fees"
                type="number"
                min="0"
                step="0.01"
                value={form.watch("fees") ?? ""}
                onChange={(e) => form.setValue("fees", e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="currency">العملة</Label>
              <select
                id="currency"
                className={selectClassName}
                value={form.watch("currency")}
                onChange={(e) => form.setValue("currency", e.target.value)}
              >
                {CURRENCY_OPTIONS.map((c) => (
                  <option key={c.value} value={c.value}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="amountPaid">المدفوع حالياً</Label>
              <Input
                id="amountPaid"
                type="number"
                min="0"
                step="0.01"
                value={form.watch("amountPaid") ?? ""}
                onChange={(e) => form.setValue("amountPaid", e.target.value)}
              />
            </div>
            <div className="space-y-2 md:col-span-3">
              <Label htmlFor="notes">ملاحظات</Label>
              <Textarea
                id="notes"
                rows={3}
                value={form.watch("notes") ?? ""}
                onChange={(e) => form.setValue("notes", e.target.value)}
              />
            </div>
          </div>
        </SectionCard>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" asChild>
            <Link href="/mandates">إلغاء</Link>
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? "جاري الحفظ..." : "حفظ الإنابة"}
          </Button>
        </div>
      </form>
    </div>
  );
}
