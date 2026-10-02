"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowRight, HardHat, Plus, Trash2 } from "lucide-react";
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
import { disputesApi } from "@/lib/api/disputes";
import { laborComplaintsApi } from "@/lib/api/labor-complaints";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { LABOR_COMPLAINT_PARTY_CAPACITY_OPTIONS } from "@/lib/labor-complaints/labels";
import { STAFF_ROLES, clientDisplayName } from "@/lib/delegations/staff";
import { cn } from "@/lib/utils/cn";
import type { LaborComplaintPartyCapacityDto } from "@/types/labor-complaint";
import type { ClientPerson } from "@/types/person";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const today = () => new Date().toISOString().slice(0, 10);

const schema = z.object({
  registrationDate: z.string().min(1, "تاريخ التسجيل مطلوب"),
  receiptDate: z.string().min(1, "تاريخ الاستلام مطلوب"),
  complaintNumber: z.string().optional(),
  complaintYear: z.string().optional(),
  subject: z.string().optional(),
  summary: z.string().optional(),
  legalRequest: z.string().optional(),
  assignedLawyerId: z.string().optional(),
  linkedDisputeId: z.string().optional(),
  internalReference: z.string().optional(),
  externalPartners: z.string().optional(),
  opponentRepresentative: z.string().optional(),
  cooperatingOffices: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;
type ClientRow = { clientId: string; capacity: LaborComplaintPartyCapacityDto | "" };
type OpponentRow = { name: string; capacity: LaborComplaintPartyCapacityDto | "" };

export default function NewLaborComplaintPage() {
  return (
    <Suspense fallback={null}>
      <NewLaborComplaintPageInner />
    </Suspense>
  );
}

function NewLaborComplaintPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const presetClientId = searchParams.get("clientId") ?? "";
  const presetDisputeId = searchParams.get("disputeId") ?? "";

  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [lawyers, setLawyers] = useState<ClientPerson[]>([]);
  const [disputes, setDisputes] = useState<{ id: string; title: string }[]>([]);
  const [clientRows, setClientRows] = useState<ClientRow[]>([{ clientId: presetClientId, capacity: "" }]);
  const [opponentRows, setOpponentRows] = useState<OpponentRow[]>([{ name: "", capacity: "" }]);
  const [clientError, setClientError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      registrationDate: today(),
      receiptDate: today(),
      complaintYear: String(new Date().getFullYear()),
      linkedDisputeId: presetDisputeId,
    },
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [clientsRes, disputesRes, ...staffPages] = await Promise.all([
          personsApi.list({ role: "CLIENT", size: 500 }),
          disputesApi.list({ size: 100 }),
          ...STAFF_ROLES.map((r) => personsApi.list({ role: r, size: 200 })),
        ]);
        if (!cancelled) {
          setClients(clientsRes.content);
          setDisputes(disputesRes.content.map((d) => ({ id: d.id, title: d.title })));
          const merged = staffPages.flatMap((p) => p.content);
          const seen = new Set<string>();
          setLawyers(merged.filter((p) => !seen.has(p.id) && seen.add(p.id)));
        }
      } catch {
        /* optional preload */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function onSubmit(values: FormValues) {
    const selectedClients = clientRows.map((r) => r.clientId).filter(Boolean);
    if (selectedClients.length === 0) {
      setClientError("اختر موكلًا واحدًا على الأقل");
      return;
    }
    setClientError(null);
    setSubmitting(true);
    setError(null);
    try {
      const opponents = opponentRows
        .filter((r) => r.name.trim())
        .map((r) => ({
          name: r.name.trim(),
          capacity: r.capacity || null,
        }));
      const year = values.complaintYear ? Number(values.complaintYear) : undefined;
      const created = await laborComplaintsApi.create({
        registrationDate: values.registrationDate,
        receiptDate: values.receiptDate,
        complaintYear: year && Number.isFinite(year) ? year : undefined,
        complaintNumber: values.complaintNumber?.trim() || null,
        subject: values.subject || null,
        summary: values.summary || null,
        legalRequest: values.legalRequest || null,
        clients: clientRows
          .filter((r) => r.clientId)
          .map((r) => ({ clientId: r.clientId, capacity: r.capacity || null })),
        assignedLawyerId: values.assignedLawyerId || null,
        opponents: opponents.length > 0 ? opponents : undefined,
        linkedDisputeId: values.linkedDisputeId || null,
        internalReference: values.internalReference || null,
        externalPartners: values.externalPartners || null,
        opponentRepresentative: values.opponentRepresentative || null,
        cooperatingOffices: values.cooperatingOffices || null,
      });
      notifySuccess("تم إنشاء الشكوى بنجاح");
      router.push(`/labor-complaints/${created.id}`);
    } catch (e) {
      const msg = e instanceof ApiRequestError ? e.message : "تعذّر إنشاء الشكوى";
      setError(msg);
      notifyApiError(e, "تعذّر إنشاء الشكوى");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      <DisputeModuleHero
        icon={HardHat}
        eyebrow="شكوى جديدة"
        title="تسجيل شكوى عمالية"
        description="تُفتح الشكوى في إدارة حماية الأجور التابعة لإدارة العمل."
        actions={
          <Button variant="outline" asChild>
            <Link href="/labor-complaints">
              <ArrowRight className="size-4" />
              العودة للقائمة
            </Link>
          </Button>
        }
      />

      {error ? <ApiErrorAlert message={error} /> : null}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        <SectionCard title="البيانات الأساسية">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="complaintNumber">رقم الشكوى (اختياري — يُولَّد تلقائياً)</Label>
              <Input id="complaintNumber" {...register("complaintNumber")} placeholder="LW-2026-000001" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="complaintYear">سنة الشكوى</Label>
              <Input id="complaintYear" type="number" {...register("complaintYear")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="registrationDate">تاريخ التسجيل *</Label>
              <Input id="registrationDate" type="date" {...register("registrationDate")} />
              {errors.registrationDate ? (
                <p className="text-xs text-red-600">{errors.registrationDate.message}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="receiptDate">تاريخ الاستلام *</Label>
              <Input id="receiptDate" type="date" {...register("receiptDate")} />
              {errors.receiptDate ? (
                <p className="text-xs text-red-600">{errors.receiptDate.message}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="internalReference">المرجع الداخلي</Label>
              <Input id="internalReference" {...register("internalReference")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="assignedLawyerId">المحامي المسؤول</Label>
              <select id="assignedLawyerId" className={selectClassName} {...register("assignedLawyerId")}>
                <option value="">—</option>
                {lawyers.map((l) => (
                  <option key={l.id} value={l.id}>
                    {clientDisplayName(l)}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="linkedDisputeId">النزاع التابع له</Label>
              <select id="linkedDisputeId" className={selectClassName} {...register("linkedDisputeId")}>
                <option value="">—</option>
                {disputes.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.title}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </SectionCard>

        <SectionCard title="تفاصيل الشكوى">
          <div className="grid gap-4">
            <div className="space-y-2">
              <Label htmlFor="subject">موضوع الشكوى</Label>
              <Input id="subject" {...register("subject")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="summary">ملخص الشكوى</Label>
              <Textarea id="summary" rows={3} {...register("summary")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="legalRequest">الطلب القانوني</Label>
              <Textarea id="legalRequest" rows={3} {...register("legalRequest")} />
            </div>
          </div>
        </SectionCard>

        <SectionCard title="الموكلون">
          {clientError ? <p className="mb-3 text-sm text-red-600">{clientError}</p> : null}
          <div className="space-y-3">
            {clientRows.map((row, idx) => (
              <div key={idx} className="grid gap-2 md:grid-cols-[1fr_180px_auto]">
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
                  disabled={clientRows.length <= 1}
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
        </SectionCard>

        <SectionCard title="الخصوم">
          <div className="space-y-3">
            {opponentRows.map((row, idx) => (
              <div key={idx} className="grid gap-2 md:grid-cols-[1fr_180px_auto]">
                <Input
                  placeholder="اسم الخصم / صاحب العمل"
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
                  disabled={opponentRows.length <= 1}
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
        </SectionCard>

        <SectionCard title="معلومات إضافية">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="externalPartners">الشركاء الخارجيون</Label>
              <Textarea id="externalPartners" rows={2} {...register("externalPartners")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="opponentRepresentative">ممثل الخصم</Label>
              <Input id="opponentRepresentative" {...register("opponentRepresentative")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="cooperatingOffices">المكاتب المتعاونة</Label>
              <Input id="cooperatingOffices" {...register("cooperatingOffices")} />
            </div>
          </div>
        </SectionCard>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" asChild>
            <Link href="/labor-complaints">إلغاء</Link>
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? "جاري الحفظ..." : "حفظ الشكوى"}
          </Button>
        </div>
      </form>
    </div>
  );
}
