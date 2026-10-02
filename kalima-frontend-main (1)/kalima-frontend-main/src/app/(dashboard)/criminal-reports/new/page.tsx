"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowRight, Plus, ShieldAlert, Trash2 } from "lucide-react";
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
import { criminalReportsApi } from "@/lib/api/criminal-reports";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  CRIMINAL_REPORT_LOCATION_TYPE_OPTIONS,
  CRIMINAL_REPORT_PARTY_CAPACITY_OPTIONS,
  PROSECUTION_OFFICES,
  SECURITY_CENTERS,
} from "@/lib/criminal-reports/labels";
import { STAFF_ROLES, clientDisplayName } from "@/lib/delegations/staff";
import { cn } from "@/lib/utils/cn";
import type { CriminalReportLocationDto, CriminalReportPartyCapacityDto } from "@/types/criminal-report";
import type { ClientPerson } from "@/types/person";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const today = () => new Date().toISOString().slice(0, 10);

const schema = z.object({
  registrationDate: z.string().min(1, "تاريخ التسجيل مطلوب"),
  reportNumber: z.string().optional(),
  incidentDate: z.string().min(1, "تاريخ الواقعة مطلوب"),
  prosecutionOffice: z.string().optional(),
  securityCenter: z.string().optional(),
  charge: z.string().optional(),
  subject: z.string().optional(),
  summary: z.string().optional(),
  legalRequest: z.string().optional(),
  assignedLawyerId: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

type ClientRow = { clientId: string; capacity: CriminalReportPartyCapacityDto | "" };
type OpponentRow = { name: string; capacity: CriminalReportPartyCapacityDto | "" };
type LocationRow = { locationType: string; locationDescription: string };

export default function NewCriminalReportPage() {
  return (
    <Suspense fallback={null}>
      <NewCriminalReportPageInner />
    </Suspense>
  );
}

function NewCriminalReportPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const presetClientId = searchParams.get("clientId") ?? "";

  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [lawyers, setLawyers] = useState<ClientPerson[]>([]);
  const [clientRows, setClientRows] = useState<ClientRow[]>([{ clientId: presetClientId, capacity: "" }]);
  const [opponentRows, setOpponentRows] = useState<OpponentRow[]>([{ name: "", capacity: "" }]);
  const [locationRows, setLocationRows] = useState<LocationRow[]>([{ locationType: "", locationDescription: "" }]);
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
      incidentDate: today(),
    },
  });

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
      const locations: CriminalReportLocationDto[] = locationRows
        .filter((r) => r.locationType || r.locationDescription.trim())
        .map((r) => ({
          locationType: r.locationType || null,
          locationDescription: r.locationDescription.trim() || null,
        }));
      const created = await criminalReportsApi.create({
        registrationDate: values.registrationDate,
        reportNumber: values.reportNumber?.trim() || null,
        incidentDate: values.incidentDate,
        prosecutionOffice: values.prosecutionOffice || null,
        securityCenter: values.securityCenter || null,
        charge: values.charge || null,
        subject: values.subject || null,
        summary: values.summary || null,
        legalRequest: values.legalRequest || null,
        clients: clientRows
          .filter((r) => r.clientId)
          .map((r) => ({ clientId: r.clientId, capacity: r.capacity || null })),
        assignedLawyerId: values.assignedLawyerId || null,
        opponents: opponents.length > 0 ? opponents : undefined,
        locations: locations.length > 0 ? locations : undefined,
      });
      notifySuccess("تم إنشاء البلاغ");
      router.push(`/criminal-reports/${created.id}`);
    } catch (e) {
      const msg = e instanceof ApiRequestError ? e.message : "تعذّر إنشاء البلاغ";
      setError(msg);
      notifyApiError(e, msg);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      <DisputeModuleHero
        icon={ShieldAlert}
        eyebrow="بلاغ جديد"
        title="تسجيل بلاغ جنائي"
        description="أدخل بيانات البلاغ الأساسية. اترك رقم البلاغ فارغاً ليُولَّد تلقائياً."
        actions={
          <Button variant="outline" asChild>
            <Link href="/criminal-reports">
              <ArrowRight className="size-4" />
              العودة للقائمة
            </Link>
          </Button>
        }
      />

      {error ? <ApiErrorAlert message={error} /> : null}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        <SectionCard title="تفاصيل البلاغ">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="reportNumber">رقم البلاغ</Label>
              <Input
                id="reportNumber"
                {...register("reportNumber")}
                placeholder="اتركه فارغاً للتوليد التلقائي — مثال: 0001/2026"
              />
              <p className="text-xs text-[var(--color-muted-foreground)]">
                يُولَّد تلقائياً عند الحفظ إذا تُرك فارغاً.
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="registrationDate">تاريخ التسجيل *</Label>
              <Input id="registrationDate" type="date" {...register("registrationDate")} />
              {errors.registrationDate ? (
                <p className="text-xs text-red-600">{errors.registrationDate.message}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="incidentDate">تاريخ الواقعة *</Label>
              <Input id="incidentDate" type="date" {...register("incidentDate")} />
              {errors.incidentDate ? (
                <p className="text-xs text-red-600">{errors.incidentDate.message}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="prosecutionOffice">النيابة / الادعاء</Label>
              <select id="prosecutionOffice" className={selectClassName} {...register("prosecutionOffice")}>
                <option value="">—</option>
                {PROSECUTION_OFFICES.map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="securityCenter">المركز الأمني</Label>
              <select id="securityCenter" className={selectClassName} {...register("securityCenter")}>
                <option value="">—</option>
                {SECURITY_CENTERS.map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="charge">التهمة</Label>
              <Input id="charge" {...register("charge")} placeholder="مثال: الاستيلاء على مال الغير" />
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label htmlFor="subject">موضوع البلاغ</Label>
              <Textarea id="subject" rows={4} {...register("subject")} />
            </div>
          </div>
        </SectionCard>

        <SectionCard title="الأطراف">
          <div className="space-y-6">
            <div className="space-y-3">
              <div className="flex items-center justify-between gap-2">
                <Label>الموكل *</Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setClientRows((rows) => [...rows, { clientId: "", capacity: "" }])}
                >
                  <Plus className="size-4" />
                  إضافة موكل
                </Button>
              </div>
              {clientRows.map((row, idx) => (
                <div key={idx} className="grid gap-2 md:grid-cols-[1fr_1fr_auto]">
                  <select
                    className={selectClassName}
                    value={row.clientId}
                    onChange={(e) => {
                      const next = [...clientRows];
                      next[idx] = { ...next[idx], clientId: e.target.value };
                      setClientRows(next);
                    }}
                  >
                    <option value="">—</option>
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
                        capacity: e.target.value as CriminalReportPartyCapacityDto | "",
                      };
                      setClientRows(next);
                    }}
                  >
                    <option value="">— صفة الموكل —</option>
                    {CRIMINAL_REPORT_PARTY_CAPACITY_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                  {clientRows.length > 1 ? (
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      aria-label="حذف الموكل"
                      onClick={() => setClientRows((rows) => rows.filter((_, i) => i !== idx))}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  ) : (
                    <span className="hidden md:block" />
                  )}
                </div>
              ))}
              {clientError ? <p className="text-xs text-red-600">{clientError}</p> : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="assignedLawyerId">المحامي المكلف</Label>
              <select id="assignedLawyerId" className={selectClassName} {...register("assignedLawyerId")}>
                <option value="">—</option>
                {lawyers.map((l) => (
                  <option key={l.id} value={l.id}>
                    {clientDisplayName(l)}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between gap-2">
                <Label>الخصم</Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setOpponentRows((rows) => [...rows, { name: "", capacity: "" }])}
                >
                  <Plus className="size-4" />
                  إضافة خصم
                </Button>
              </div>
              {opponentRows.map((row, idx) => (
                <div key={idx} className="grid gap-2 md:grid-cols-[1fr_1fr_auto]">
                  <Input
                    placeholder="اسم الخصم"
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
                        capacity: e.target.value as CriminalReportPartyCapacityDto | "",
                      };
                      setOpponentRows(next);
                    }}
                  >
                    <option value="">— صفة الخصم —</option>
                    {CRIMINAL_REPORT_PARTY_CAPACITY_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                  {opponentRows.length > 1 ? (
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      aria-label="حذف الخصم"
                      onClick={() => setOpponentRows((rows) => rows.filter((_, i) => i !== idx))}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  ) : (
                    <span className="hidden md:block" />
                  )}
                </div>
              ))}
            </div>
          </div>
        </SectionCard>

        <SectionCard title="الطلب القانوني">
          <Textarea rows={4} {...register("legalRequest")} placeholder="الطلب القانوني المقدّم..." />
        </SectionCard>

        <SectionCard title="مواقع الواقعة">
          <div className="space-y-3">
            <div className="flex items-center justify-between gap-2">
              <Label>المواقع</Label>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() =>
                  setLocationRows((rows) => [...rows, { locationType: "", locationDescription: "" }])
                }
              >
                <Plus className="size-4" />
                إضافة موقع
              </Button>
            </div>
            {locationRows.map((row, idx) => (
              <div key={idx} className="grid gap-2 md:grid-cols-[1fr_1fr_auto]">
                <select
                  className={selectClassName}
                  value={row.locationType}
                  onChange={(e) => {
                    const next = [...locationRows];
                    next[idx] = { ...next[idx], locationType: e.target.value };
                    setLocationRows(next);
                  }}
                >
                  <option value="">— نوع الموقع —</option>
                  {CRIMINAL_REPORT_LOCATION_TYPE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
                <Input
                  placeholder="وصف الموقع"
                  value={row.locationDescription}
                  onChange={(e) => {
                    const next = [...locationRows];
                    next[idx] = { ...next[idx], locationDescription: e.target.value };
                    setLocationRows(next);
                  }}
                />
                {locationRows.length > 1 ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    aria-label="حذف الموقع"
                    onClick={() => setLocationRows((rows) => rows.filter((_, i) => i !== idx))}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                ) : (
                  <span className="hidden md:block" />
                )}
              </div>
            ))}
          </div>
        </SectionCard>

        <SectionCard title="ملخص البلاغ">
          <Textarea rows={4} {...register("summary")} placeholder="ملخص مختصر للبلاغ..." />
        </SectionCard>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" asChild>
            <Link href="/criminal-reports">إلغاء</Link>
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? "جاري الحفظ..." : "حفظ البلاغ"}
          </Button>
        </div>
      </form>
    </div>
  );
}
