"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRight, Handshake, Plus } from "lucide-react";

import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { DisputeWorkflowHorizontal } from "@/components/disputes/DisputeWorkflowHorizontal";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";
import { disputesApi } from "@/lib/api/disputes";
import { personsApi } from "@/lib/api/persons";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  disputeCreateSchema,
  type DisputeCreateFormValues,
} from "@/lib/validation/dispute-create";
import { DISPUTE_PARTY_CAPACITY_OPTIONS, DISPUTE_TYPE_AR, LEGAL_PATH_AR, RISK_LEVEL_AR } from "@/lib/disputes/labels";
import type { DisputeCreateRequestDto, LegalPathDto } from "@/types/dispute";
import type { ClientPerson } from "@/types/person";
import type { Staff } from "@/types/staff";

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

export default function NewDisputePage() {
  const router = useRouter();
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [adminStaff, setAdminStaff] = useState<Staff[]>([]);
  const [loadPicklists, setLoadPicklists] = useState(true);
  const [pickError, setPickError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<DisputeCreateFormValues>({
    resolver: zodResolver(disputeCreateSchema),
    defaultValues: {
      title: "",
      clientId: "",
      clientRole: undefined,
      opponentName: "",
      opponentRole: undefined,
      description: "",
      disputeType: "CIVIL",
      claimAmount: undefined,
      riskLevel: "MEDIUM",
      legalPath: "",
      administrativeOfficerId: "",
      successProbability: undefined,
    },
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoadPicklists(true);
      setPickError(null);
      try {
        const [cRes, secRes, admRes] = await Promise.all([
          personsApi.list({ role: "CLIENT", size: 500 }),
          staffApi.list({ role: "SECRETARY", size: 200 }),
          staffApi.list({ role: "ADMIN", size: 100 }),
        ]);
        if (cancelled) return;
        const adminMap = new Map<string, Staff>();
        for (const p of [...secRes.content, ...admRes.content]) {
          if (p.isActive) adminMap.set(p.id, p);
        }
        setClients(cRes.content.filter((p) => p.active && !p.archivedAt));
        setAdminStaff([...adminMap.values()]);
      } catch (e) {
        if (cancelled) return;
        setPickError(
          e instanceof ApiRequestError
            ? e.details?.message ?? e.message
            : "تعذر تحميل القوائم",
        );
      } finally {
        if (!cancelled) setLoadPicklists(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function onSubmit(data: DisputeCreateFormValues) {
    setSubmitError(null);
    setSubmitting(true);
    try {
      const body: DisputeCreateRequestDto = {
        title: data.title.trim(),
        clientId: data.clientId,
        opponentName: data.opponentName.trim(),
        disputeType: data.disputeType,
        riskLevel: data.riskLevel,
      };
      const desc = data.description?.trim();
      if (desc) body.description = desc;
      if (data.claimAmount != null && !Number.isNaN(data.claimAmount)) {
        body.claimAmount = data.claimAmount;
      }
      if (data.successProbability != null && !Number.isNaN(data.successProbability)) {
        body.successProbability = data.successProbability;
      }
      if (data.legalPath && data.legalPath !== "") {
        body.legalPath = data.legalPath as LegalPathDto;
      }
      if (data.clientRole) body.clientRole = data.clientRole;
      if (data.opponentRole) body.opponentRole = data.opponentRole;
      const adminId = data.administrativeOfficerId?.trim();
      if (adminId) body.administrativeOfficerId = adminId;

      const created = await disputesApi.create(body);
      notifySuccess("تم إنشاء النزاع");
      router.push(`/disputes/${created.id}`);
    } catch (e) {
      notifyApiError(e, "تعذر حفظ النزاع");
      if (e instanceof ApiRequestError) {
        setSubmitError(e.details?.message ?? e.message);
      } else {
        setSubmitError("تعذر حفظ النزاع");
      }
    } finally {
      setSubmitting(false);
    }
  }

  const selectedClient = clients.find((c) => c.id === form.watch("clientId"));

  return (
    <div dir="rtl" className="space-y-6">
      <DisputeModuleHero
        eyebrow="ملف جديد"
        title="إضافة نزاع"
        description="سجّل نزاعاً جديداً، ثم تابع المراحل والقضايا المرتبطة من صفحة التفاصيل."
        icon={Handshake}
        actions={
          <Button
            variant="outline"
            asChild
            className="border-white/25 bg-white/10 text-white hover:bg-white/15 hover:text-white"
          >
            <Link href="/disputes" className="inline-flex items-center gap-2">
              <ArrowRight className="size-4" />
              العودة للقائمة
            </Link>
          </Button>
        }
      />

      {pickError ? <ApiErrorAlert message={pickError} /> : null}

      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="mx-auto max-w-4xl space-y-6"
      >
        {submitError ? <ApiErrorAlert message={submitError} /> : null}

        <SectionCard title="معلومات النزاع">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="disputeNoNote">رقم النزاع</Label>
              <p
                id="disputeNoNote"
                className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 px-3 py-2.5 text-sm text-[var(--color-muted-foreground)]"
              >
                يتم توليد رقم النزاع تلقائياً بعد الحفظ (مثل DIS-2026-0001).
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="riskLevel">الخطورة</Label>
              <select
                id="riskLevel"
                className={selectClassName}
                {...form.register("riskLevel")}
              >
                {(Object.entries(RISK_LEVEL_AR) as [keyof typeof RISK_LEVEL_AR, string][]).map(
                  ([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ),
                )}
              </select>
            </div>
          </div>
          <div className="mt-4 space-y-2 sm:col-span-2">
            <Label htmlFor="title">عنوان النزاع</Label>
            <Input id="title" {...form.register("title")} />
            {form.formState.errors.title ? (
              <p className="text-sm text-[var(--color-danger)]">
                {form.formState.errors.title.message}
              </p>
            ) : null}
          </div>
        </SectionCard>

        <SectionCard
          title="أطراف النزاع"
          action={
            <Button type="button" variant="outline" size="sm" className="gap-1" disabled>
              <Plus className="size-4" />
              إضافة طرف
            </Button>
          }
        >
          <p className="mb-4 text-xs text-[var(--color-muted-foreground)]">
            حالياً: عميل واحد من القائمة وخصم بنص حر. دعم أطراف متعددة لاحقاً.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="clientId">العميل (الطرف الرئيسي)</Label>
              <select
                id="clientId"
                className={selectClassName}
                disabled={loadPicklists}
                {...form.register("clientId")}
              >
                <option value="">— اختر عميلاً —</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>
                    {clientLabel(c)}
                  </option>
                ))}
              </select>
              {form.formState.errors.clientId ? (
                <p className="text-sm text-[var(--color-danger)]">
                  {form.formState.errors.clientId.message}
                </p>
              ) : null}
              {selectedClient ? (
                <p className="text-xs text-[var(--color-muted-foreground)]">
                  نوع الشخص: {selectedClient.personType === "COMPANY" ? "منشأة" : "فرد"}
                </p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="clientRole">صفة العميل</Label>
              <select
                id="clientRole"
                className={selectClassName}
                {...form.register("clientRole")}
              >
                <option value="">— اختر الصفة —</option>
                {DISPUTE_PARTY_CAPACITY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="opponentName">الخصم / الطرف الآخر</Label>
              <Input id="opponentName" {...form.register("opponentName")} />
              {form.formState.errors.opponentName ? (
                <p className="text-sm text-[var(--color-danger)]">
                  {form.formState.errors.opponentName.message}
                </p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="opponentRole">صفة الخصم</Label>
              <select
                id="opponentRole"
                className={selectClassName}
                {...form.register("opponentRole")}
              >
                <option value="">— اختر الصفة —</option>
                {DISPUTE_PARTY_CAPACITY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </SectionCard>

        <SectionCard title="فريق العمل">
          <p className="mb-4 text-xs text-[var(--color-muted-foreground)]">
            عند الإنشاء يُحدَّد المسؤول الإداري فقط. يمكن إكمال باقي الفريق من صفحة
            تفاصيل النزاع بعد الحفظ.
          </p>
          <div className="max-w-md space-y-2">
            <Label htmlFor="administrativeOfficerId">المسؤول الإداري</Label>
            <select
              id="administrativeOfficerId"
              className={selectClassName}
              disabled={loadPicklists}
              {...form.register("administrativeOfficerId")}
            >
              <option value="">— اختر مسؤولاً —</option>
              {adminStaff.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fullName?.trim() || p.email || p.id}
                </option>
              ))}
            </select>
          </div>
        </SectionCard>

        <SectionCard
          title="خط سير النزاع (معاينة)"
          description="يُضبط المسار الكامل بعد حفظ النزاع وإضافة المراحل."
          action={
            <Button type="button" variant="gold" size="sm" className="gap-1" disabled>
              <Plus className="size-4" />
              إضافة مرحلة
            </Button>
          }
        >
          <p className="mb-4 text-xs text-[var(--color-muted-foreground)]">
            بعد حفظ النزاع يمكنك إضافة مراحل حقيقية (بلاغ جنائي، دعوى مدنية، إلخ) من صفحة
            التفاصيل.
          </p>
          <DisputeWorkflowHorizontal currentStep="CREATED" draftMode />
        </SectionCard>

        <SectionCard title="التصنيف والقيمة">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="disputeType">نوع النزاع</Label>
              <select
                id="disputeType"
                className={selectClassName}
                {...form.register("disputeType")}
              >
                {(Object.entries(DISPUTE_TYPE_AR) as [keyof typeof DISPUTE_TYPE_AR, string][]).map(
                  ([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ),
                )}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="legalPath">المسار القانوني المقترح</Label>
              <select id="legalPath" className={selectClassName} {...form.register("legalPath")}>
                <option value="">— بدون —</option>
                {LEGAL_PATH_OPTS.map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="claimAmount">قيمة المطالبة (اختياري)</Label>
              <Input
                id="claimAmount"
                type="number"
                min={0}
                step="0.01"
                {...form.register("claimAmount")}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="successProbability">احتمال النجاح % (٠–١٠٠)</Label>
              <Input
                id="successProbability"
                type="number"
                min={0}
                max={100}
                {...form.register("successProbability")}
              />
            </div>
          </div>
        </SectionCard>

        <SectionCard title="تفاصيل النزاع">
          <Textarea
            id="description"
            rows={6}
            placeholder="اكتب تفاصيل النزاع، الوقائع، والأهداف الاستراتيجية…"
            className="min-h-[140px] resize-y rounded-xl"
            {...form.register("description")}
          />
        </SectionCard>

        <div className="flex flex-wrap justify-end gap-3 border-t border-[var(--color-border)] pt-6">
          <Button type="button" variant="outline" asChild>
            <Link href="/disputes">إلغاء</Link>
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={submitting || loadPicklists}
            onClick={() => void form.handleSubmit(onSubmit)()}
          >
            {submitting ? "جارٍ الحفظ…" : "حفظ كمسودة"}
          </Button>
          <Button type="submit" variant="gold" disabled={submitting || loadPicklists}>
            {submitting ? "جارٍ الحفظ…" : "حفظ وإضافة النزاع"}
          </Button>
        </div>
        <p className="text-center text-xs text-[var(--color-muted-foreground)]">
          «حفظ كمسودة» و«حفظ وإضافة النزاع» يقيمان بنفس الطلب حالياً حتى يتوفر مسار مسودات في
          الخادم.
        </p>
      </form>
    </div>
  );
}
